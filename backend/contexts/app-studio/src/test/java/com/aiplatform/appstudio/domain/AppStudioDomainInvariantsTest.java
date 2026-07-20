package com.aiplatform.appstudio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppStudioDomainInvariantsTest {
    private static final TenantId TENANT_ID = TenantId.of("1001");
    private static final Instant NOW = Instant.parse("2026-05-31T00:00:00Z");

    @Test
    void aiApplicationRejectsBlankRequiredFactsInsteadOfNullOnlyValidation() {
        assertThatThrownBy(() -> new AiApplication(
                null,
                "app-1",
                AiApplicationType.KNOWLEDGE_QA,
                "客服助手",
                "企业客服",
                "9001",
                List.of("kb-1"),
                List.of(),
                Set.of("user-1"),
                "回答企业客服问题",
                ApplicationPublicationStatus.PUBLISHED,
                NOW,
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");

        assertThatThrownBy(() -> new AiApplication(
                TENANT_ID,
                " ",
                AiApplicationType.KNOWLEDGE_QA,
                "客服助手",
                "企业客服",
                "9001",
                List.of("kb-1"),
                List.of(),
                Set.of("user-1"),
                "回答企业客服问题",
                ApplicationPublicationStatus.PUBLISHED,
                NOW,
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("应用 ID不能为空");

        assertThatThrownBy(() -> new AiApplication(
                TENANT_ID,
                "app-1",
                null,
                "客服助手",
                "企业客服",
                "9001",
                List.of("kb-1"),
                List.of(),
                Set.of("user-1"),
                "回答企业客服问题",
                ApplicationPublicationStatus.PUBLISHED,
                NOW,
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("应用类型不能为空");

        assertThatThrownBy(() -> new AiApplication(
                TENANT_ID,
                "app-1",
                AiApplicationType.KNOWLEDGE_QA,
                "客服助手",
                "企业客服",
                "9001",
                List.of(" "),
                List.of(),
                Set.of("user-1"),
                "回答企业客服问题",
                ApplicationPublicationStatus.PUBLISHED,
                NOW,
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("知识库 ID不能为空");

        assertThatThrownBy(() -> new AiApplication(
                TENANT_ID,
                "app-1",
                AiApplicationType.KNOWLEDGE_QA,
                "客服助手",
                "企业客服",
                "9001",
                List.of("kb-1"),
                List.of(),
                Set.of("user-1"),
                "回答企业客服问题",
                null,
                NOW,
                NOW
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("发布状态不能为空");
    }

    @Test
    void aiApplicationTrimsRequiredFactsBeforeUse() {
        AiApplication application = new AiApplication(
                TENANT_ID,
                " app-1 ",
                AiApplicationType.KNOWLEDGE_QA,
                " 客服助手 ",
                " 企业客服 ",
                " 9001 ",
                List.of(" kb-1 ", "kb-1"),
                List.of(" tool-1 "),
                Set.of(" user-1 "),
                " 回答企业客服问题 ",
                ApplicationPublicationStatus.PUBLISHED,
                NOW,
                NOW
        );

        assertThat(application.applicationId()).isEqualTo("app-1");
        assertThat(application.name()).isEqualTo("客服助手");
        assertThat(application.modelConfigId()).isEqualTo("9001");
        assertThat(application.knowledgeBaseIds()).containsExactly("kb-1");
        assertThat(application.toolIds()).containsExactly("tool-1");
        assertThat(application.visiblePrincipalIds()).containsExactly("user-1");
        assertThat(application.systemPrompt()).isEqualTo("回答企业客服问题");
    }

    @Test
    void chatSessionRejectsBlankIdentifiersInsteadOfNullOnlyValidation() {
        assertThatThrownBy(() -> new ChatSession(null, "session-1", "app-1", "release-1", 1, "user-1", "会话", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> new ChatSession(TENANT_ID, " ", "app-1", "release-1", 1, "user-1", "会话", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会话 ID不能为空");
        assertThatThrownBy(() -> new ChatSession(TENANT_ID, "session-1", "app-1", "release-1", 1, "user-1", "会话", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("创建时间不能为空");

        assertThatThrownBy(() -> new ChatSession(TENANT_ID, "session-1", "app-1", " ", 1, "user-1", "会话", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("发布版本 ID不能为空");
        assertThatThrownBy(() -> new ChatSession(TENANT_ID, "session-1", "app-1", "release-1", 0, "user-1", "会话", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("发布版本号必须大于 0");

        ChatSession session = new ChatSession(
                TENANT_ID, " session-1 ", " app-1 ", " release-1 ", 1, " user-1 ", " 会话标题 ", NOW, NOW
        );

        assertThat(session.sessionId()).isEqualTo("session-1");
        assertThat(session.applicationId()).isEqualTo("app-1");
        assertThat(session.releaseId()).isEqualTo("release-1");
        assertThat(session.releaseVersion()).isEqualTo(1L);
        assertThat(session.userId()).isEqualTo("user-1");
        assertThat(session.title()).isEqualTo("会话标题");
    }

    @Test
    void chatMessageRejectsBlankContentInsteadOfNullOnlyValidation() {
        assertThatThrownBy(() -> new ChatMessage(null, "msg-1", "session-1", ChatRole.USER, "你好", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> new ChatMessage(TENANT_ID, "msg-1", "session-1", null, "你好", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("消息角色不能为空");
        assertThatThrownBy(() -> new ChatMessage(TENANT_ID, "msg-1", "session-1", ChatRole.USER, " ", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("消息内容不能为空");
        assertThatThrownBy(() -> new ChatMessage(TENANT_ID, "msg-1", "session-1", ChatRole.USER, "你好", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("创建时间不能为空");

        List<Citation> citations = new ArrayList<>();
        citations.add(null);
        assertThatThrownBy(() -> new ChatMessage(TENANT_ID, "msg-1", "session-1", ChatRole.USER, "你好", citations, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("引用来源不能为空");

        ChatMessage message = new ChatMessage(
                TENANT_ID,
                " msg-1 ",
                " session-1 ",
                ChatRole.USER,
                " 你好 ",
                List.of(),
                NOW
        );

        assertThat(message.messageId()).isEqualTo("msg-1");
        assertThat(message.sessionId()).isEqualTo("session-1");
        assertThat(message.content()).isEqualTo("你好");
    }

    @Test
    void citationRejectsBlankFactsAndInvalidScore() {
        assertThatThrownBy(() -> new Citation("kb-1", " ", "chunk-1", "企业手册", "片段", "p1", 0.8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档 ID不能为空");

        assertThatThrownBy(() -> new Citation("kb-1", "doc-1", "chunk-1", "企业手册", "片段", "p1", Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("引用分数必须在 0 到 1 之间");

        Citation citation = new Citation(" kb-1 ", " doc-1 ", " chunk-1 ", " 企业手册 ", " 片段 ", " p1 ", 0.8);

        assertThat(citation.knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(citation.documentId()).isEqualTo("doc-1");
        assertThat(citation.chunkId()).isEqualTo("chunk-1");
        assertThat(citation.documentName()).isEqualTo("企业手册");
        assertThat(citation.fragment()).isEqualTo("片段");
        assertThat(citation.location()).isEqualTo("p1");
    }
}
