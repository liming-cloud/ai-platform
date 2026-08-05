package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out.EvaluationDatasetRegistrationStore;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetContentClassification;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 验证评测数据集仅以可审计元数据进入 PostgreSQL，私有正文不会形成数据库旁路。
 */
@SpringBootTest(
        classes = PostgresEvaluationDatasetRegistrationStoreTest.TestApplication.class,
        properties = {
            "foundation.persistence.snowflake.worker-id=1",
            "foundation.persistence.snowflake.datacenter-id=1",
            "foundation.assistant-delivery.enabled=false",
            "foundation.evaluation-operations.enabled=false",
            "foundation.knowledge-data.enabled=false",
            "foundation.model-runtime.enabled=false",
            "foundation.organization-access.enabled=false",
            "foundation.trusted-query.enabled=false",
            "foundation.evaluation-operations.persistence.enabled=true"
        })
@Testcontainers
class PostgresEvaluationDatasetRegistrationStoreTest {

    private static final TenantId STORE_TENANT = new TenantId(100000000000000001L);
    private static final TenantId CAMPUS_TENANT = new TenantId(100000000000000002L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T05:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine3.24")
                    .withDatabaseName("foundation_test")
                    .withUsername("foundation")
                    .withPassword("foundation_test_only");

    @Autowired
    private EvaluationDatasetRegistrationStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/evaluation-operations");
    }

    @BeforeEach
    void clearDatasets() {
        jdbc.update("DELETE FROM evaluation_operations.evaluation_dataset");
    }

    @Test
    void 应按租户提交清单且表结构不含任何私有正文列() {
        store.commit(dataset(STORE_TENANT));
        store.commit(dataset(CAMPUS_TENANT));

        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM evaluation_operations.evaluation_dataset WHERE tenant_id = ?
                """, Long.class, STORE_TENANT.value()));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM evaluation_operations.evaluation_dataset WHERE tenant_id = ?
                """, Long.class, CAMPUS_TENANT.value()));
        var columns = Set.copyOf(jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'evaluation_operations' AND table_name = 'evaluation_dataset'
                """, String.class));
        assertFalse(columns.stream().anyMatch(column ->
                column.contains("content") && !column.equals("content_digest") && !column.equals("private_content_reference")));
        assertFalse(columns.stream().anyMatch(column ->
                column.contains("question") || column.contains("answer") || column.contains("body")));
    }

    private static EvaluationDataset dataset(TenantId tenantId) {
        return EvaluationDataset.create(
                        tenantId,
                        new EvaluationDatasetId(510000000000000001L),
                        "private://evaluation/tenants/" + tenantId.value() + "/datasets/510000000000000001",
                        "a".repeat(64),
                        3,
                        EvaluationDatasetContentClassification.SYNTHETIC,
                        "retention-30d",
                        CREATED_AT)
                .publish();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
