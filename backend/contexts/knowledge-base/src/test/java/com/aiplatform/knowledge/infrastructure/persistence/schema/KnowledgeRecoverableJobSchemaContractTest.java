package com.aiplatform.knowledge.infrastructure.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeRecoverableJobSchemaContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..", "..", "..").toAbsolutePath().normalize();
    private static final Path SCHEMA = REPOSITORY_ROOT.resolve("deploy/postgres/init/01-schema.sql");

    @Test
    void schemaAddsDurableLeaseRetryAndFrozenIndexTargetFacts() throws IOException {
        String sql = Files.readString(SCHEMA);

        assertThat(sql)
                .contains("CREATE TABLE public.kb_embedding_job")
                .contains("failure_code")
                .contains("lease_owner")
                .contains("lease_token")
                .contains("lease_until")
                .contains("target_model_config_id")
                .contains("target_embedding_model")
                .contains("index_version")
                .contains("ck_kb_job_retry_count")
                .contains("RETRY_WAIT")
                .contains("PARSING")
                .contains("INDEXING");
    }

    @Test
    void schemaFreezesMembershipAndPersistsSingleDesiredGenerationGuard() throws IOException {
        String sql = Files.readString(SCHEMA);

        assertThat(sql)
                .contains("CREATE TABLE public.kb_knowledge_index_rebuild")
                .contains("CREATE TABLE public.kb_knowledge_index_rebuild_member")
                .contains("document_updated_at")
                .contains("expected_chunk_count")
                .contains("CREATE TABLE public.kb_knowledge_index_desired")
                .contains("desired_rebuild_id")
                .contains("desired_generation")
                .contains("next_generation_required")
                .contains("uk_kb_index_rebuild_active");
    }

    @Test
    void schemaContainsTerminalReconciliationSupport() throws IOException {
        String sql = Files.readString(SCHEMA);

        assertThat(sql)
                .contains("idx_kb_job_document_latest")
                .contains("ck_kb_job_failure")
                .contains("failure_code character varying(64)");
    }
}
