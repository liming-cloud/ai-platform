package com.aiplatform.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeBaseTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-05-22T08:00:00Z");

    @Test
    void rejectsBlankKnowledgeBaseIdInsteadOfInvalidAggregateRoot() {
        assertThatThrownBy(() -> knowledgeBase(" ", "企业制度知识库", "用于制度问答"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库ID不能为空");
    }

    @Test
    void rejectsBlankNameInsteadOfUnnamedKnowledgeBase() {
        assertThatThrownBy(() -> knowledgeBase("7001", " ", "用于制度问答"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("知识库名称不能为空");
    }

    @Test
    void allowsEmptyDescriptionBecauseDescriptionIsOptional() {
        KnowledgeBase knowledgeBase = knowledgeBase("7001", "企业制度知识库", "");

        assertThat(knowledgeBase.description()).isEmpty();
    }

    @Test
    void normalizesCreationAndAclFactsInsideAggregateRoot() {
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                TENANT_ID,
                " 7001 ",
                " 企业制度知识库 ",
                " 用于制度问答 ",
                KnowledgeVisibility.SPECIFIED_MEMBERS,
                Set.of(" 1001 "),
                " 1001 ",
                " 17 ",
                true,
                true,
                NOW,
                NOW
        );

        assertThat(knowledgeBase.knowledgeBaseId()).isEqualTo("7001");
        assertThat(knowledgeBase.name()).isEqualTo("企业制度知识库");
        assertThat(knowledgeBase.description()).isEqualTo("用于制度问答");
        assertThat(knowledgeBase.allowedPrincipalIds()).containsExactly("1001");
        assertThat(knowledgeBase.createdBy()).isEqualTo("1001");
        assertThat(knowledgeBase.canBeRetrievedBy(" 1001 ")).isTrue();
        assertThat(knowledgeBase.retrievalAclTokensFor(" 1001 ")).containsExactly("kb:7001");
    }

    @Test
    void rejectsBlankCreatedByAndAclPrincipal() {
        assertThatThrownBy(() -> new KnowledgeBase(
                        TENANT_ID,
                        "7001",
                        "企业制度知识库",
                        "用于制度问答",
                        KnowledgeVisibility.PUBLIC,
                        Set.of("1001"),
                        " ",
                        "17",
                        true,
                        true,
                        NOW,
                        NOW
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("知识库创建人不能为空");
        assertThatThrownBy(() -> new KnowledgeBase(
                        TENANT_ID,
                        "7001",
                        "企业制度知识库",
                        "用于制度问答",
                        KnowledgeVisibility.PUBLIC,
                        Set.of(" "),
                        "1001",
                        "17",
                        true,
                        true,
                        NOW,
                        NOW
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("授权主体不能为空");
    }

    private static KnowledgeBase knowledgeBase(String knowledgeBaseId, String name, String description) {
        return new KnowledgeBase(
                TENANT_ID,
                knowledgeBaseId,
                name,
                description,
                KnowledgeVisibility.PUBLIC,
                Set.of("1001"),
                "1001",
                "embedding-model-1",
                true,
                true,
                NOW,
                NOW
        );
    }
}
