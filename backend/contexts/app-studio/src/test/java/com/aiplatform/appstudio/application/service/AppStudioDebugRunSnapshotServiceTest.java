package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionResult;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCitation;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AppStudioDebugRunSnapshotServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-06-16T08:38:00Z");

    @Test
    void sendMessagePersistsEvidenceSnapshotForSuccessfulAssistantRun() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        AppStudioDebugRunSnapshotPort snapshotPort = Mockito.mock(AppStudioDebugRunSnapshotPort.class);
        AppStudioDebugRunQueryPort queryPort = Mockito.mock(AppStudioDebugRunQueryPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeRetrievalCitation retrievalCitation = new KnowledgeRetrievalCitation("20", "18", "1", "企业手册", "命中片段", "p1", 0.88);
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence("20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1");
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(true, List.of(retrievalCitation), List.of(evidence), null));
        when(policyPort.decide(any())).thenReturn(allowDecision());
        when(completionPort.complete(any())).thenReturn(new ChatCompletionResult(
                "基于知识库的回答",
                List.of(new Citation("20", "18", "1", "企业手册", "命中片段", "p1", 0.88)),
                "88",
                Map.of("providerCode", "siliconflow")
        ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                snapshotPort,
                queryPort,
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.sendMessage(new SendChatMessageCommand(TENANT_ID, "1001", "4001", "差旅报销规则是什么？", "trace-1"));

        ArgumentCaptor<AppStudioDebugRunSnapshot> captor = ArgumentCaptor.forClass(AppStudioDebugRunSnapshot.class);
        Mockito.verify(snapshotPort).record(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().assistantMessageId()).isEqualTo("5002");
        assertThat(captor.getValue().traceId()).isEqualTo("trace-1");
        assertThat(captor.getValue().evidences()).hasSize(1);
        assertThat(captor.getValue().evidences().get(0).knowledgeBaseId()).isEqualTo("20");
    }

    @Test
    void sendMessagePersistsEvidenceSnapshotForFailClosedRun() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        AppStudioDebugRunSnapshotPort snapshotPort = Mockito.mock(AppStudioDebugRunSnapshotPort.class);
        AppStudioDebugRunQueryPort queryPort = Mockito.mock(AppStudioDebugRunQueryPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence("20", "18", "1", 0.42, "VECTOR", "kb-20-active-v1");
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(false, List.of(), List.of(evidence), "知识库未召回可用证据"));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                snapshotPort,
                queryPort,
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.sendMessage(new SendChatMessageCommand(TENANT_ID, "1001", "4001", "差旅报销规则是什么？", "trace-1"));

        ArgumentCaptor<AppStudioDebugRunSnapshot> captor = ArgumentCaptor.forClass(AppStudioDebugRunSnapshot.class);
        Mockito.verify(snapshotPort).record(captor.capture());
        assertThat(captor.getValue().assistantMessageId()).isEqualTo("5002");
        assertThat(captor.getValue().evidences()).hasSize(1);
        assertThat(captor.getValue().evidences().get(0).score()).isEqualTo(0.42d);
    }

    private static AppModelUsageDecision allowDecision() {
        return new AppModelUsageDecision(
                true,
                "ALLOW",
                "命中已发布策略",
                new AppModelRuntimeDescriptor(
                        "6",
                        "3001",
                        "siliconflow",
                        "openai-compatible",
                        new com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef(
                                "https://api.siliconflow.cn/v1",
                                "api_key",
                                "secret-1"
                        ),
                        "qwen-plus",
                        7,
                        "CHAT",
                        Set.of("chat-completion"),
                        Map.of(),
                        "a".repeat(64),
                        "v1"
                ),
                Map.of("policy", "app")
        );
    }

    private static AiApplication application() {
        return new AiApplication(
                TENANT_ID,
                "3001",
                AiApplicationType.KNOWLEDGE_QA,
                "客服助手",
                "企业客服",
                "9001",
                List.of("20"),
                List.of(),
                Set.of("1001"),
                "请基于知识库回答",
                ApplicationPublicationStatus.PUBLISHED,
                NOW,
                NOW
        );
    }

    private static ChatSession session() {
        return new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "1001", "制度问答", NOW, NOW);
    }

    private static PermissionCheckPort allowAllPermissionCheck() {
        PermissionCheckPort permissionCheckPort = Mockito.mock(PermissionCheckPort.class);
        when(permissionCheckPort.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        return permissionCheckPort;
    }

    private static RbacAuthorizationQueryPort allowAllAuthorizationQuery() {
        RbacAuthorizationQueryPort authorizationQueryPort = Mockito.mock(RbacAuthorizationQueryPort.class);
        when(authorizationQueryPort.findPermissionCodes(any(), any(), any()))
                .thenReturn(Set.of(
                        new PermissionCode("app-studio:application:create"),
                        new PermissionCode("app-studio:application:read"),
                        new PermissionCode("app-studio:application:update"),
                        new PermissionCode("app-studio:application:publish"),
                        new PermissionCode("app-studio:application:use"),
                        new PermissionCode("app-studio:model:use")
                ));
        return authorizationQueryPort;
    }
}
