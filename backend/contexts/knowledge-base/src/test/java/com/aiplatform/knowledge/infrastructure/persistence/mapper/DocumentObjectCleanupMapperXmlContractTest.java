package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentObjectCleanupMapperXmlContractTest {
    @Test
    void claimAndTerminalWritesUseSkipLockedTenantLeaseAndFence() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .containsPattern("(?s)<select id=\"claimNext\".*FOR UPDATE SKIP LOCKED.*version = cleanup.version \\+ 1.*RETURNING.*</select>")
                .contains("lease_owner = #{workerId}")
                .contains("lease_token = #{leaseToken}")
                .containsPattern("(?s)<update id=\"completeOwned\".*tenant_id = #\\{tenantId}.*lease_owner = #\\{leaseOwner}.*lease_token = #\\{leaseToken}.*version = #\\{fenceVersion}.*lease_until &gt; #\\{now}.*</update>")
                .containsPattern("(?s)<update id=\"retryOwned\".*status = 'RETRY_WAIT'.*next_attempt_at.*tenant_id = #\\{tenantId}.*version = #\\{fenceVersion}.*</update>")
                .containsPattern("(?s)<update id=\"retainOwned\".*status = 'RETAINED'.*tenant_id = #\\{tenantId}.*version = #\\{fenceVersion}.*</update>");
    }

    @Test
    void liveReferenceCheckRequiresExactTenantDocumentUriAndCurrentOwnership() throws IOException {
        String xml = mapperXml();
        assertThat(xml)
                .containsPattern("(?s)<select id=\"hasLiveDocumentReference\".*cleanup.tenant_id = #\\{tenantId}.*cleanup.id = #\\{cleanupId}.*cleanup.lease_owner = #\\{leaseOwner}.*cleanup.lease_token = #\\{leaseToken}.*cleanup.version = #\\{fenceVersion}.*</select>")
                .contains("document.tenant_id = cleanup.tenant_id")
                .contains("document.knowledge_base_id = cleanup.knowledge_base_id")
                .contains("document.id = cleanup.document_id")
                .contains("document.source_uri = cleanup.source_uri");
    }

    private static String mapperXml() throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("mapper/knowledge/DocumentObjectCleanupMapper.xml")) {
            assertThat(input).as("DocumentObjectCleanupMapper.xml").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
