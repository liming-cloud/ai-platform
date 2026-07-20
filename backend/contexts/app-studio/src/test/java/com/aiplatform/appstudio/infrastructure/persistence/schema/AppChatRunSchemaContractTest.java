package com.aiplatform.appstudio.infrastructure.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppChatRunSchemaContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..", "..", "..").toAbsolutePath().normalize();
    private static final Path SCHEMA = REPOSITORY_ROOT.resolve("deploy/postgres/init/01-schema.sql");

    @Test
    void schemaEnforcesTenantOwnedChatRunReferences() throws IOException {
        String sql = Files.readString(SCHEMA);

        assertThat(sql)
                .contains("CREATE TABLE public.app_chat_run")
                .contains("uk_iam_user_tenant_id")
                .contains("uk_app_application_tenant_id")
                .contains("uk_app_chat_session_tenant_id")
                .contains("fk_app_chat_run_session_tenant")
                .contains("fk_app_chat_run_application_tenant")
                .contains("fk_app_chat_run_actor_tenant");
    }

    @Test
    void schemaPersistsBoundedVersionedFinalizationAndResultPayloads() throws IOException {
        String sql = Files.readString(SCHEMA);

        assertThat(sql)
                .contains("finalization_json jsonb")
                .contains("result_json jsonb")
                .contains("ck_app_chat_run_finalization_payload")
                .contains("ck_app_chat_run_finalization_schema")
                .contains("ck_app_chat_run_result_schema")
                .contains("ck_app_chat_run_quarantine_phase");
    }

    @Test
    void schemaBoundsRecoveryAttemptsAndPersistsReplayIdempotency() throws IOException {
        String sql = Files.readString(SCHEMA);

        assertThat(sql)
                .contains("CREATE FUNCTION public.app_chat_run_finalization_counter_guard()")
                .contains("finalization_attempt_count integer DEFAULT 0 NOT NULL")
                .contains("finalization_cycle_attempt_count integer DEFAULT 0 NOT NULL")
                .contains("ck_app_chat_run_finalization_retry_counts")
                .contains("CREATE TABLE public.app_chat_run_replay_request")
                .contains("uk_app_chat_run_replay_request_id")
                .contains("fk_app_chat_run_replay_run_tenant")
                .contains("idx_app_chat_run_dead_letter");
    }
}
