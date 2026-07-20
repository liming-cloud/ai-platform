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

class ModelUsagePolicyOutboxMapperXmlTest {
    private static final String RESOURCE = "mapper/modelhub/ModelUsagePolicyOutboxMapper.xml";

    @Test
    void policyRevisionComesFromThePersistentDatabaseSequence() throws Exception {
        MappedStatement statement = statement("nextPolicyRevision");
        String sql = normalized(statement.getBoundSql(Map.of()).getSql());

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(sql).contains("nextval('mdl_model_usage_policy_revision_seq')");
    }

    @Test
    void canonicalDigestUsesTheDatabaseOwnedPolicyPayloadFunction() throws Exception {
        MappedStatement statement = statement("canonicalDigest");
        String sql = normalized(statement.getBoundSql(Map.of("payloadJson", "{}" )).getSql());

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(sql).contains("mdl_model_usage_policy_digest(cast(? as jsonb))");
    }

    @Test
    void enqueuePersistsTheImmutableEnvelopeAsPendingWithoutPublishingSideEffects() throws Exception {
        MappedStatement statement = statement("enqueue");
        String sql = normalized(statement.getBoundSql(Map.of("message", message())).getSql());

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
        assertThat(sql)
                .contains("insert into mdl_model_usage_policy_outbox")
                .contains("cast(? as jsonb)")
                .contains("'pending'")
                .contains("on conflict (event_id) do nothing");
    }

    @Test
    void claimIsAnAtomicPostgresLeaseWithSkipLockedAndExpiredLeaseRecovery() throws Exception {
        MappedStatement statement = statement("claimBatch");
        String sql = normalized(statement.getBoundSql(Map.of(
                "workerId", "worker-1",
                "now", Instant.parse("2026-07-17T08:00:00Z"),
                "leaseUntil", Instant.parse("2026-07-17T08:00:30Z"),
                "limit", 20
        )).getSql());

        assertThat(statement.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(statement.isFlushCacheRequired()).isTrue();
        assertThat(sql)
                .contains("for update skip locked")
                .contains("status = 'pending'")
                .contains("status = 'in_flight'")
                .contains("lease_until <=")
                .contains("status = 'in_flight'")
                .contains("lease_owner =")
                .contains("attempts = outbox.attempts + 1")
                .contains("returning");
    }

    @Test
    void completionAndRetryAreBothFencedByLeaseOwner() throws Exception {
        String publishedSql = normalized(statement("markPublished").getBoundSql(Map.of(
                "id", 91L,
                "workerId", "worker-1",
                "now", Instant.parse("2026-07-17T08:00:00Z")
        )).getSql());
        String retrySql = normalized(statement("markRetry").getBoundSql(Map.of(
                "id", 91L,
                "workerId", "worker-1",
                "now", Instant.parse("2026-07-17T08:00:00Z"),
                "nextAttemptAt", Instant.parse("2026-07-17T08:00:10Z"),
                "lastError", "IllegalStateException"
        )).getSql());

        assertThat(publishedSql)
                .contains("status = 'in_flight'")
                .contains("lease_owner =");
        assertThat(retrySql)
                .contains("status = 'pending'")
                .contains("status = 'in_flight'")
                .contains("lease_owner =")
                .contains("next_attempt_at =");
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ModelUsagePolicyOutboxMapper.class.getName() + "." + id);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static DomainEventMessage message() {
        return new DomainEventMessage(
                "event-model-policy-91",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                "4001",
                1,
                "v1",
                Instant.parse("2026-07-17T07:59:00Z"),
                "trace-model-policy-91",
                "model-hub",
                "{\"modelConfigId\":\"4001\"}"
        );
    }
}
