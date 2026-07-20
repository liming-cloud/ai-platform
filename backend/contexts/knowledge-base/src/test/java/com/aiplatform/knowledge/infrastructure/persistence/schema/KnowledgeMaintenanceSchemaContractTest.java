package com.aiplatform.knowledge.infrastructure.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KnowledgeMaintenanceSchemaContractTest {
    private static final Path REPOSITORY_ROOT = Path.of("..", "..", "..").toAbsolutePath().normalize();
    private static final Path SCHEMA = REPOSITORY_ROOT.resolve("deploy/postgres/init/01-schema.sql");
    private static final Path RUNTIME_POLICY =
            REPOSITORY_ROOT.resolve("deploy/postgres/runtime-object-policy.sql");

    @Test
    void schemaPersistsRecoverableTenantScopedObjectCleanupFacts() throws IOException {
        String sql = Files.readString(SCHEMA);

        assertThat(sql)
                .contains("CREATE TABLE public.kb_document_object_cleanup")
                .contains("ck_kb_object_cleanup_tenant_key")
                .contains("ck_kb_object_cleanup_lease")
                .contains("idx_kb_object_cleanup_due")
                .contains("idx_kb_chunk_embedding_cleanup")
                .contains("lease_owner")
                .contains("lease_token")
                .contains("RETRY_WAIT")
                .contains("RETAINED");
    }

    @Test
    void runtimePolicyClassifiesMaintenanceRelations() throws IOException {
        String policy = Files.readString(RUNTIME_POLICY);

        assertThat(policy)
                .contains("('kb_document_object_cleanup', 'business_dml')")
                .contains("('kb_document_chunk_embedding', 'business_dml')");
    }
}
