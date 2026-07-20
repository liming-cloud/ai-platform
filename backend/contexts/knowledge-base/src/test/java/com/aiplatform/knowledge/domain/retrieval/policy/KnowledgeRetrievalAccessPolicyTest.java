package com.aiplatform.knowledge.domain.retrieval.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.retrieval.RetrievalAccessFilter;
import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalAccessPolicyTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-05-22T08:00:00Z");

    private final KnowledgeRetrievalAccessPolicy policy = new KnowledgeRetrievalAccessPolicy();

    @Test
    void normalizesPrincipalBeforeAclTokenGeneration() {
        KnowledgeBase knowledgeBase = knowledgeBase(KnowledgeVisibility.SPECIFIED_MEMBERS, Set.of("1001"));

        RetrievalAccessFilter filter = policy.accessFilter(TENANT_ID, " 1001 ", List.of(knowledgeBase), NOW);

        assertThat(filter.aclTokens()).contains("user:1001", "owner:1001", "kb:7001");
        assertThat(filter.aclTokens()).doesNotContain("user: 1001 ", "owner: 1001 ");
        assertThat(filter.allows(candidateWithAcl("user:1001"))).isTrue();
        assertThat(filter.allows(candidateWithAcl("user: 1001 "))).isFalse();
    }

    @Test
    void normalizesPrincipalBeforeKnowledgeBaseAuthorization() {
        KnowledgeBase knowledgeBase = knowledgeBase(KnowledgeVisibility.SPECIFIED_MEMBERS, Set.of("1001"));

        assertThat(policy.authorizedBases(TENANT_ID, " 1001 ", List.of(knowledgeBase))).containsExactly(knowledgeBase);
    }

    @Test
    void includesServerSideRolePermissionAndClassificationTokens() {
        KnowledgeBase knowledgeBase = knowledgeBase(KnowledgeVisibility.PUBLIC, Set.of());

        RetrievalAccessFilter filter = policy.accessFilter(
                TENANT_ID,
                "1001",
                List.of(knowledgeBase),
                Set.of("finance"),
                Set.of(new PermissionCode("knowledge-base:classification-confidential:read")),
                NOW
        );

        assertThat(filter.aclTokens()).contains(
                "tenant:2001",
                "role:finance",
                "permission:knowledge-base:classification-confidential:read",
                "classification:confidential"
        );
    }

    private static KnowledgeBase knowledgeBase(KnowledgeVisibility visibility, Set<String> allowedPrincipalIds) {
        return new KnowledgeBase(
                TENANT_ID,
                "7001",
                "企业制度知识库",
                "用于制度问答",
                visibility,
                allowedPrincipalIds,
                "1001",
                "embedding-model-1",
                true,
                true,
                NOW,
                NOW
        );
    }

    private static RetrievalCandidate candidateWithAcl(String aclToken) {
        return new RetrievalCandidate(
                "2001",
                "7001",
                "8001",
                "9001",
                "制度片段",
                0.91,
                1,
                RetrievalSignal.VECTOR,
                new KnowledgeDocumentChunkMetadata(
                        "s3://aip-knowledge/tenant/2001/doc.txt",
                        1,
                        "制度/审批",
                        null,
                        null,
                        "internal",
                        "tenant",
                        List.of(aclToken),
                        false,
                        NOW,
                        null,
                        "embedding-model-unassigned",
                        "heading:v1",
                        "kb-7001-doc-v1"
                )
        );
    }
}
