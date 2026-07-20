package com.aiplatform.appstudio.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppChatRunMapperXmlTest {

    @Test
    void everyStateMutationIsTenantOwnedAndFenced() throws Exception {
        String xml = mapperXml();

        assertThat(xml)
                .contains("tenant_id = #{tenantId}")
                .contains("run_id = #{runId}")
                .contains("lease_owner = #{leaseOwner}")
                .contains("fencing_token = #{fencingToken}")
                .contains("status = 'IN_PROGRESS'")
                .contains("phase = 'PREPARING'")
                .contains("phase = 'MODEL_DISPATCHED'")
                .contains("status = 'UNCERTAIN'");
    }

    @Test
    void claimUsesDatabaseConflictControlAndLocksCanonicalRow() throws Exception {
        String xml = mapperXml();

        assertThat(xml.toLowerCase())
                .contains("on conflict (tenant_id, session_id, client_message_id) do nothing")
                .contains("for update");
    }

    @Test
    void resultReceivedRecoveryIsFencedAndNeverReentersModelDispatch() throws Exception {
        String xml = mapperXml();

        assertThat(xml)
                .contains("id=\"recordResultReceived\"")
                .contains("phase = 'RESULT_RECEIVED'")
                .contains("finalization_json = CAST(#{finalizationJson} AS jsonb)")
                .contains("id=\"claimRecoverableResults\"")
                .contains("FOR UPDATE SKIP LOCKED")
                .contains("lease_owner = #{leaseOwner}")
                .contains("fencing_token = #{fencingToken}")
                .contains("finalization_json = NULL")
                .contains("status = 'IN_PROGRESS' AND phase = 'RESULT_RECEIVED'");
    }

    @Test
    void invalidFinalizationQuarantineIsTenantOwnedFencedAndPayloadFree() throws Exception {
        String quarantine = statement(mapperXml(), "id=\"quarantineInvalidFinalization\"");

        assertThat(quarantine)
                .contains("status = 'QUARANTINED'")
                .contains("finalization_json = NULL")
                .contains("failure_code = #{failureCode}")
                .contains("failure_summary = #{failureSummary}")
                .contains("tenant_id = #{tenantId}")
                .contains("run_id = #{runId}")
                .contains("lease_owner = #{leaseOwner}")
                .contains("fencing_token = #{fencingToken}")
                .contains("status = 'IN_PROGRESS'")
                .contains("phase = 'RESULT_RECEIVED'")
                .doesNotContain("#{finalizationJson}");
    }

    @Test
    void finalizationRecoveryHasBoundedAttemptsBackoffAndDeadLetterPayloadRetention() throws Exception {
        String xml = mapperXml();
        String failure = statement(xml, "id=\"recordFinalizationFailure\"");

        assertThat(xml)
                .contains("finalization_cycle_attempt_count &lt; #{maxAttempts}")
                .contains("finalization_attempt_count = run.finalization_attempt_count + 1")
                .contains("finalization_cycle_attempt_count = run.finalization_cycle_attempt_count + 1");
        assertThat(failure)
                .contains("status = CASE")
                .contains("'DEAD_LETTER'")
                .contains("lease_expires_at")
                .contains("INTERVAL '1 second'")
                .contains("failure_trace_id = #{traceId}")
                .doesNotContain("finalization_json = NULL");
    }

    @Test
    void manualReplayIsTenantScopedIdempotentAndFenced() throws Exception {
        String replay = statement(mapperXml(), "id=\"claimDeadLetterForReplay\"");

        assertThat(replay)
                .contains("tenant_id = #{tenantId}")
                .contains("run_id = #{runId}")
                .contains("status = 'DEAD_LETTER'")
                .contains("last_replay_request_id = #{replayRequestId}")
                .contains("fencing_token = fencing_token + 1")
                .contains("manual_replay_count = manual_replay_count + 1")
                .contains("finalization_cycle_attempt_count = 1")
                .doesNotContain("finalization_json = NULL");
        assertThat(mapperXml().toLowerCase())
                .contains("insert into app_chat_run_replay_request")
                .contains("on conflict (tenant_id, replay_request_id) do nothing");
    }

    private static String statement(String xml, String id) {
        int start = xml.indexOf(id);
        assertThat(start).as("mapper statement %s must exist", id).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</update>", start);
        assertThat(end).as("mapper statement %s must close", id).isGreaterThan(start);
        return xml.substring(start, end);
    }

    private static String mapperXml() throws Exception {
        try (InputStream stream = AppChatRunMapperXmlTest.class.getResourceAsStream(
                "/mapper/appstudio/AppChatRunMapper.xml")) {
            assertThat(stream).as("AppChatRunMapper.xml must exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }
}
