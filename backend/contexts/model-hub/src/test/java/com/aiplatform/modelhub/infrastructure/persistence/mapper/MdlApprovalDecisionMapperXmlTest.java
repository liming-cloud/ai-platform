package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlConfigPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlProviderPersistenceEntity;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MdlApprovalDecisionMapperXmlTest {
    @Test
    void genericMutationUpdatesAreTenantVersionAndStatusFenced() throws Exception {
        String providerSql = normalized(statement(
                "mapper/modelhub/MdlProviderMapper.xml",
                MdlProviderMapper.class,
                "compareAndSet"
        ).getBoundSql(Map.of(
                "updated", providerDecision(),
                "expectedVersion", 7,
                "expectedStatus", "ACTIVE"
        )).getSql());
        String configSql = normalized(statement(
                "mapper/modelhub/MdlConfigMapper.xml",
                MdlConfigMapper.class,
                "compareAndSet"
        ).getBoundSql(Map.of(
                "updated", configDecision(),
                "expectedVersion", 7,
                "expectedStatus", "ACTIVE"
        )).getSql());

        assertThat(providerSql)
                .contains("where tenant_id = ?")
                .contains("and id = ?")
                .contains("and deleted_at is null")
                .contains("and version = ?")
                .contains("and status = ?")
                .contains("version = ? + 1");
        assertThat(configSql)
                .contains("where tenant_id = ?")
                .contains("and provider_id = ?")
                .contains("and id = ?")
                .contains("and deleted_at is null")
                .contains("and version = ?")
                .contains("and status = ?")
                .contains("version = ? + 1");
    }

    @Test
    void providerDecisionUpdateIsTenantPendingRequestAndDeletionFenced() throws Exception {
        String sql = normalized(statement(
                "mapper/modelhub/MdlProviderMapper.xml",
                MdlProviderMapper.class,
                "compareAndSetApprovalDecision"
        ).getBoundSql(Map.of(
                "decision", providerDecision(),
                "expectedVersion", 7,
                "expectedStatus", "PENDING_APPROVAL",
                "expectedApprovalRequestId", "REQ-TEST",
                "expectedEvidenceDigest", "a".repeat(64),
                "expectedTargetDigest", "b".repeat(64)
        )).getSql());

        assertThat(sql)
                .contains("update mdl_provider")
                .contains("set status = ?")
                .contains("updated_at = ?")
                .contains("version = ? + 1")
                .contains("where tenant_id = ?")
                .contains("and id = ?")
                .contains("and deleted_at is null")
                .contains("and version = ?")
                .contains("and status = ?")
                .contains("and approval_request_id = ?")
                .contains("and approval_evidence_digest = ?")
                .contains("and approval_target_digest = ?");
    }

    @Test
    void configDecisionUpdateIsTenantPendingRequestAndDeletionFencedAndClearsConnectivity() throws Exception {
        String sql = normalized(statement(
                "mapper/modelhub/MdlConfigMapper.xml",
                MdlConfigMapper.class,
                "compareAndSetApprovalDecision"
        ).getBoundSql(Map.of(
                "decision", configDecision(),
                "expectedVersion", 7,
                "expectedStatus", "PENDING_APPROVAL",
                "expectedApprovalRequestId", "REQ-TEST",
                "expectedEvidenceDigest", "a".repeat(64),
                "expectedTargetDigest", "b".repeat(64)
        )).getSql());

        assertThat(sql)
                .contains("update mdl_config")
                .contains("set status = ?")
                .contains("connectivity_status = ?")
                .contains("connectivity_checked_at = null")
                .contains("connectivity_message = ?")
                .contains("updated_at = ?")
                .contains("version = ? + 1")
                .contains("where tenant_id = ?")
                .contains("and id = ?")
                .contains("and deleted_at is null")
                .contains("and version = ?")
                .contains("and status = ?")
                .contains("and approval_request_id = ?")
                .contains("and approval_evidence_digest = ?")
                .contains("and approval_target_digest = ?");
    }

    private static MappedStatement statement(String resource, Class<?> mapper, String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(mapper.getName() + "." + id);
    }

    private static MdlProviderPersistenceEntity providerDecision() {
        MdlProviderPersistenceEntity decision = new MdlProviderPersistenceEntity();
        decision.setTenantId(2001L);
        decision.setId(3001L);
        decision.setStatus("ACTIVE");
        decision.setUpdatedAt(Instant.parse("2026-05-25T08:00:00Z"));
        return decision;
    }

    private static MdlConfigPersistenceEntity configDecision() {
        MdlConfigPersistenceEntity decision = new MdlConfigPersistenceEntity();
        decision.setTenantId(2001L);
        decision.setId(4001L);
        decision.setStatus("REJECTED");
        decision.setConnectivityStatus("NOT_TESTED");
        decision.setConnectivityMessage("");
        decision.setUpdatedAt(Instant.parse("2026-05-25T08:00:00Z"));
        return decision;
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
