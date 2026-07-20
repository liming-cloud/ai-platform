package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Claim;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
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

class AppStudioChatRunRecoveryTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

    @Test
    void resultReceivedRetryReplaysOnlyFinalizationAndNeverCallsProviderAgain() {
        AiApplicationRepository applications = mock(AiApplicationRepository.class);
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policy = mock(ModelUsagePolicyPort.class);
        ChatCompletionPort provider = mock(ChatCompletionPort.class);
        ChatRunTransactionService transactions = mock(ChatRunTransactionService.class);
        PermissionCheckPort permissions = mock(PermissionCheckPort.class);
        RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
        SendChatMessageCommand command = new SendChatMessageCommand(
                TENANT_ID, "1001", "4001", "client-1", "hello", "trace-1"
        );
        ChatRunRepository.Ownership ownership = new ChatRunRepository.Ownership(
                TENANT_ID, "9001", "4001", "3001", "1001", "client-1", "recovery-worker", 8
        );
        Finalization finalization = finalization();
        when(applications.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessions.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(permissions.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        when(authorization.findPermissionCodes(any(), any(), any())).thenReturn(Set.of(
                new PermissionCode("app-studio:application:read"),
                new PermissionCode("app-studio:application:use")
        ));
        when(transactions.claim(command, "3001")).thenReturn(Claim.resultReceived(ownership, finalization));
        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.createWithChatRuns(
                applications,
                sessions,
                retrieval,
                policy,
                provider,
                transactions,
                mock(AppStudioDebugRunQueryPort.class),
                permissions,
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ChatMessageResult result = service.sendMessage(command);

        assertThat(result).isEqualTo(finalization.result());
        verify(transactions).complete(ownership, finalization);
        verifyNoInteractions(retrieval, policy, provider);
    }

    private static AiApplication application() {
        return new AiApplication(
                TENANT_ID, "3001", AiApplicationType.KNOWLEDGE_QA, "客服助手", "企业客服", "9001",
                List.of("20"), List.of(), Set.of("1001"), "请基于知识库回答",
                ApplicationPublicationStatus.PUBLISHED, NOW, NOW
        );
    }

    private static ChatSession session() {
        return new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "1001", "制度问答", NOW, NOW);
    }

    private static Finalization finalization() {
        ChatMessage user = new ChatMessage(
                TENANT_ID, "5001", "4001", ChatRole.USER, "hello", List.of(), null,
                "client-1", "trace-1", NOW
        );
        ChatMessage assistant = new ChatMessage(
                TENANT_ID, "5002", "4001", ChatRole.ASSISTANT, "answer", List.of(), null,
                null, "trace-1", NOW
        );
        ChatMessageResult result = new ChatMessageResult(
                "5001", "5002", "4001", "3001", "answer", true,
                List.of(), List.of(), null, 0, NOW, Map.of("traceId", "trace-1")
        );
        return new Finalization(
                user,
                assistant,
                new AppStudioDebugRunSnapshot(TENANT_ID, "5002", "trace-1", NOW, List.of()),
                result
        );
    }
}
