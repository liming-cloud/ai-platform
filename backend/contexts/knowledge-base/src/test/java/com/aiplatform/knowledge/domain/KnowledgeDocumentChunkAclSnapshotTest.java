package com.aiplatform.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentChunkAclSnapshotTest {
    @Test
    void normalizesAclTokensBeforeRetrievalUse() {
        KnowledgeDocumentChunkAclSnapshot snapshot = new KnowledgeDocumentChunkAclSnapshot(List.of(
                " tenant:2001 ",
                "kb:7001",
                "tenant:2001"
        ));

        assertThat(snapshot.tokens()).containsExactly("tenant:2001", "kb:7001");
    }

    @Test
    void rejectsBlankAclTokenInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> new KnowledgeDocumentChunkAclSnapshot(List.of("tenant:2001", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限快照不能为空白");
    }

    @Test
    void rejectsNullAclTokenInsteadOfFilteringBadInput() {
        assertThatThrownBy(() -> new KnowledgeDocumentChunkAclSnapshot(Arrays.asList("tenant:2001", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("权限快照不能为空白");
    }

    @Test
    void forDocumentNormalizesOperatorIdBeforeOwnerToken() {
        KnowledgeDocumentChunkAclSnapshot snapshot = KnowledgeDocumentChunkAclSnapshot.forDocument(
                document(),
                " 1001 "
        );

        assertThat(snapshot.tokens()).containsExactly("tenant:2001", "kb:7001", "owner:1001");
    }

    private static KnowledgeDocument document() {
        Instant now = Instant.parse("2026-05-25T08:00:00Z");
        return new KnowledgeDocument(
                TenantId.of("2001"),
                "8001",
                "7001",
                "faq.txt",
                "s3",
                "s3://bucket/faq.txt",
                "hash-001",
                "text/plain",
                128,
                1,
                KnowledgeDocumentStatus.CHUNKED,
                now,
                now,
                "1001"
        );
    }
}
