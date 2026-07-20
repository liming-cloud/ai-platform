package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ModelApprovalRequestOutboxMapperXmlTest {
    private static final String RESOURCE = "mapper/modelhub/ModelApprovalRequestOutboxMapper.xml";

    @Test
    void enqueuePersistsImmutableEnvelopeWithoutBrokerSideEffects() throws Exception {
        MappedStatement statement = statement("enqueue");
        String sql = normalized(statement.getBoundSql(Map.of("message", message())).getSql());

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
        assertThat(sql)
                .contains("insert into mdl_approval_request_outbox")
                .contains("cast(? as jsonb)")
                .contains("'pending'")
                .contains("on conflict (event_id) do nothing");
    }

    @Test
    void claimAtomicallyRecoversPendingAndExpiredLeases() throws Exception {
        MappedStatement statement = statement("claimBatch");
        String sql = normalized(statement.getBoundSql(Map.of(
                "workerId", "approval-request-worker-1",
                "now", Instant.parse("2026-07-20T08:00:00Z"),
                "leaseUntil", Instant.parse("2026-07-20T08:00:30Z"),
                "limit", 20
        )).getSql());

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(statement.isFlushCacheRequired()).isTrue();
        assertThat(sql)
                .contains("for update skip locked")
                .contains("status = 'pending'")
                .contains("status = 'in_flight'")
                .contains("lease_until <=")
                .contains("attempts = outbox.attempts + 1")
                .contains("returning");
    }

    @Test
    void completionAndRetryAreFencedByLeaseOwner() throws Exception {
        String published = normalized(statement("markPublished").getBoundSql(Map.of(
                "id", 91L,
                "workerId", "approval-request-worker-1",
                "now", Instant.parse("2026-07-20T08:00:00Z")
        )).getSql());
        String retry = normalized(statement("markRetry").getBoundSql(Map.of(
                "id", 91L,
                "workerId", "approval-request-worker-1",
                "now", Instant.parse("2026-07-20T08:00:00Z"),
                "nextAttemptAt", Instant.parse("2026-07-20T08:00:05Z"),
                "lastError", "IllegalStateException"
        )).getSql());

        assertThat(published).contains("status = 'in_flight'", "lease_owner =");
        assertThat(retry).contains("status = 'pending'", "status = 'in_flight'", "lease_owner =", "next_attempt_at =");
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ModelApprovalRequestOutboxMapper.class.getName() + "." + id);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static DomainEventMessage message() {
        return new DomainEventMessage(
                "ModelConfigApprovalRequested:2001:model_config:REQ-91",
                "ModelConfigApprovalRequested",
                "2001",
                "ModelConfig",
                "4001",
                1,
                "v1",
                Instant.parse("2026-07-20T07:59:00Z"),
                "trace-approval-request-91",
                "model-hub",
                "{\"approvalRequestId\":\"REQ-91\"}"
        );
    }
}
