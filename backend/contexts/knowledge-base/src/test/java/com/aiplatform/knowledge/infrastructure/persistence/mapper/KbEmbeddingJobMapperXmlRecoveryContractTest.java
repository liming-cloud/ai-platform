package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KbEmbeddingJobMapperXmlRecoveryContractTest {
    @Test
    void claimsDueOrExpiredJobsWithSkipLockedLeaseAndFence() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .containsPattern("(?s)<select id=\"claimNext\".*FOR UPDATE SKIP LOCKED.*lease_owner.*lease_token.*lease_until.*version = job.version \\+ 1.*RETURNING.*</select>")
                .contains("status IN ('PENDING', 'RETRY_WAIT')")
                .contains("status IN ('PARSING', 'INDEXING')")
                .contains("job.retry_count &lt;= #{maxAttempts}")
                .doesNotContain("status IN ('PENDING', 'RETRY_WAIT', 'FAILED')");
    }

    @Test
    void terminalAndRetryWritesRequireExactLeaseAndFence() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .containsPattern("(?s)<update id=\"completeOwned\".*lease_owner = #\\{leaseOwner}.*lease_token = #\\{leaseToken}.*version = #\\{fenceVersion}.*</update>")
                .containsPattern("(?s)<update id=\"retryOwned\".*status = 'RETRY_WAIT'.*next_retry_at.*lease_token = #\\{leaseToken}.*version = #\\{fenceVersion}.*</update>")
                .containsPattern("(?s)<update id=\"failOwned\".*status = 'FAILED'.*lease_token = #\\{leaseToken}.*version = #\\{fenceVersion}.*</update>");
    }

    @Test
    void heartbeatRenewsOnlyTheExactLiveFenceWithoutChangingItsVersion() throws IOException {
        assertThat(mapperXml())
                .containsPattern("(?s)<update id=\"renewOwned\".*lease_until = #\\{leaseUntil}.*tenant_id = #\\{tenantId}.*lease_owner = #\\{leaseOwner}.*lease_token = #\\{leaseToken}.*version = #\\{fenceVersion}.*lease_until &gt; #\\{now}.*</update>")
                .doesNotContain("<update id=\"renewOwned\">\n        UPDATE kb_embedding_job\n        SET version");
    }

    @Test
    void manualRetryIsTheOnlySqlTransitionOutOfFailed() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .containsPattern("(?s)<update id=\"retryFailedManually\".*status = 'PENDING'.*retry_count = 0.*WHERE.*status = 'FAILED'.*</update>")
                .doesNotContain("status IN ('PENDING', 'RETRY_WAIT', 'FAILED')");
    }

    @Test
    void latestRecoverySnapshotIsStableAndFullyTenantKnowledgeBaseScoped() throws IOException {
        assertThat(mapperXml())
                .containsPattern("(?s)<select id=\"findLatestSnapshots\".*DISTINCT ON \\(job.document_id\\).*tenant_id = #\\{tenantId}.*knowledge_base_id = #\\{knowledgeBaseId}.*document_id IN.*ORDER BY job.document_id, job.created_at DESC, job.id DESC.*</select>");
    }

    @Test
    void cancellationUpdatesOnlyTheLatestScopedNonTerminalJobAndInvalidatesItsFence() throws IOException {
        assertThat(mapperXml())
                .containsPattern("(?s)<select id=\"cancelLatest\".*tenant_id = #\\{tenantId}.*knowledge_base_id = #\\{knowledgeBaseId}.*document_id = #\\{documentId}.*ORDER BY job.created_at DESC, job.id DESC.*LIMIT 1.*FOR UPDATE.*status IN \\('PENDING', 'RETRY_WAIT', 'PARSING', 'INDEXING'\\).*</select>")
                .containsPattern("(?s)<select id=\"cancelLatest\".*SET status = 'CANCELLED'.*cancel_reason = #\\{reason}.*cancelled_by = #\\{operatorId}.*cancel_trace_id = #\\{traceId}.*cancelled_at = #\\{now}.*lease_owner = NULL.*lease_token = NULL.*lease_until = NULL.*version = job.version \\+ 1.*RETURNING job\\.\\*.*</select>");
    }

    private static String mapperXml() throws IOException {
        try (var input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("mapper/knowledge/KbEmbeddingJobMapper.xml")) {
            assertThat(input).as("KbEmbeddingJobMapper.xml").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
