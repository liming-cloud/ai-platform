package com.aiplatform.approval.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApprovalDecisionOutboxMapperXmlTest {
    private static final String RESOURCE = "mapper/approval/ApprovalDecisionOutboxMapper.xml";

    @Test
    void enqueueIsUniqueAndHasNoBrokerSideEffect() throws Exception {
        String sql = normalized(statement("enqueue").getBoundSql(Map.of("message", message())).getSql());

        assertThat(sql)
                .contains("insert into appr_decision_outbox")
                .contains("cast(? as bigint)")
                .contains("cast(? as jsonb)")
                .contains("'pending'")
                .contains("on conflict (event_id) do nothing");
    }

    @Test
    void claimAtomicallyRecoversPendingAndExpiredLeasesAfterRestart() throws Exception {
        String sql = normalized(statement("claimBatch").getBoundSql(Map.of(
                "workerId", "approval-worker-1",
                "now", Instant.parse("2026-07-18T08:00:00Z"),
                "leaseUntil", Instant.parse("2026-07-18T08:00:30Z"),
                "limit", 20
        )).getSql());

        assertThat(sql)
                .contains("status = 'pending'")
                .contains("status = 'in_flight' and outbox.lease_until <=")
                .contains("for update skip locked")
                .contains("attempts = outbox.attempts + 1")
                .contains("returning");
    }

    @Test
    void successAndRetryUpdatesAreFencedByTheExactLeaseOwner() throws Exception {
        String published = normalized(statement("markPublished").getBoundSql(Map.of(
                "id", 91L, "workerId", "approval-worker-1", "now", Instant.parse("2026-07-18T08:00:00Z")
        )).getSql());
        String retry = normalized(statement("markRetry").getBoundSql(Map.of(
                "id", 91L,
                "workerId", "approval-worker-1",
                "now", Instant.parse("2026-07-18T08:00:00Z"),
                "nextAttemptAt", Instant.parse("2026-07-18T08:00:05Z"),
                "lastError", "IllegalStateException"
        )).getSql());

        assertThat(published).contains("status = 'in_flight'").contains("lease_owner = ?");
        assertThat(retry)
                .contains("status = 'pending'")
                .contains("status = 'in_flight'")
                .contains("lease_owner = ?")
                .contains("next_attempt_at = ?");
    }

    private static MappedStatement statement(String id) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement(ApprovalDecisionOutboxMapper.class.getName() + "." + id);
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private static DomainEventMessage message() {
        return new DomainEventMessage(
                "ApprovalDecisionRecorded:2001:7001:APPROVED",
                "ApprovalDecisionRecorded",
                "2001",
                "ApprovalTask",
                "7001",
                1,
                "v1",
                Instant.parse("2026-07-18T08:00:00Z"),
                "trace-approval-7001",
                "approval-center",
                "{\"taskId\":\"7001\"}"
        );
    }
}
