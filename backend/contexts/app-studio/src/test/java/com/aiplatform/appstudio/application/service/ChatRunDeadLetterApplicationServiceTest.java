package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.ReplayChatRunDeadLetterCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ReplayClaim;
import com.aiplatform.appstudio.application.result.ChatRunManualReplayResult;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.appstudio.presentation.permission.AppStudioPermissionCatalog;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatRunDeadLetterApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    private ChatRunRepository repository;
    private ChatRunManualReplayTransactionService manualReplayTransactions;
    private ChatRunTransactionService finalizationTransactions;
    private RbacAuthorizationQueryPort authorization;
    private ChatRunDeadLetterApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ChatRunRepository.class);
        manualReplayTransactions = mock(ChatRunManualReplayTransactionService.class);
        finalizationTransactions = mock(ChatRunTransactionService.class);
        authorization = mock(RbacAuthorizationQueryPort.class);
        service = new ChatRunDeadLetterApplicationService(
                repository, manualReplayTransactions, finalizationTransactions, authorization
        );
    }

    @Test
    void deniesReplayToPublisherWithoutAdministrativeAclPermission() {
        when(authorization.findPermissionCodes(TENANT_ID, "user", "1002"))
                .thenReturn(Set.of(AppStudioPermissionCatalog.APPLICATION_PUBLISH));

        assertThatThrownBy(() -> service.replay(command()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).errorCode())
                        .isEqualTo(AppStudioErrorCodes.AUTHORIZATION_DENIED));

        verify(manualReplayTransactions, never()).claim(command());
    }

    @Test
    void deniesReplayToAclManagerWithoutPublishPermission() {
        when(authorization.findPermissionCodes(TENANT_ID, "user", "1002"))
                .thenReturn(Set.of(AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL));

        assertThatThrownBy(() -> service.replay(command()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).errorCode())
                        .isEqualTo(AppStudioErrorCodes.AUTHORIZATION_DENIED));

        verify(manualReplayTransactions, never()).claim(command());
    }

    @Test
    void listsOnlyTheAuthenticatedTenantDeadLetters() {
        when(authorization.findPermissionCodes(TENANT_ID, "user", "1002"))
                .thenReturn(administratorPermissions());
        when(repository.findDeadLetters(TENANT_ID, 20)).thenReturn(List.of());

        assertThat(service.listDeadLetters(TENANT_ID, "1002", 20)).isEmpty();

        verify(repository).findDeadLetters(TENANT_ID, 20);
    }

    @Test
    void rejectsUnboundedDeadLetterQueriesBeforeAuthorizationOrPersistence() {
        assertThatThrownBy(() -> service.listDeadLetters(TENANT_ID, "1002", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 100");
        assertThatThrownBy(() -> service.listDeadLetters(TENANT_ID, "1002", 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 到 100");

        verifyNoInteractions(authorization, repository);
    }

    @Test
    void acquiredReplayCompletesOnlyThePersistedFinalizationFacts() {
        when(authorization.findPermissionCodes(TENANT_ID, "user", "1002"))
                .thenReturn(administratorPermissions());
        ReplayClaim claim = ReplayClaim.acquired(
                ChatRunTestFixtures.ownership(),
                ChatRunTestFixtures.finalization()
        );
        when(manualReplayTransactions.claim(command())).thenReturn(claim);

        ChatRunManualReplayResult result = service.replay(command());

        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(finalizationTransactions).complete(claim.ownership(), claim.finalization());
    }

    @Test
    void sameReplayRequestIsIdempotentAndDoesNotFinalizeAgain() {
        when(authorization.findPermissionCodes(TENANT_ID, "user", "1002"))
                .thenReturn(administratorPermissions());
        ReplayClaim claim = ReplayClaim.alreadyAccepted("IN_PROGRESS", ChatRunTestFixtures.ownership());
        when(manualReplayTransactions.claim(command())).thenReturn(claim);

        ChatRunManualReplayResult result = service.replay(command());

        assertThat(result.status()).isEqualTo("IN_PROGRESS");
        verifyNoInteractions(finalizationTransactions);
    }

    private static ReplayChatRunDeadLetterCommand command() {
        return new ReplayChatRunDeadLetterCommand(
                TENANT_ID, "9001", "1002", "replay-1", "数据库恢复后人工重放", "trace-1"
        );
    }

    private static Set<com.aiplatform.shared.security.model.PermissionCode> administratorPermissions() {
        return Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL
        );
    }

    /** Keeps security-sensitive persisted facts shared and visibly canonical in these tests. */
    private static final class ChatRunTestFixtures {
        private static ChatRunRepository.Ownership ownership() {
            return new ChatRunRepository.Ownership(
                    TENANT_ID, "9001", "4001", "3001", "1001", "client-1", "replay-owner", 8
            );
        }

        private static ChatRunRepository.Finalization finalization() {
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
            return new ChatRunRepository.Finalization(
                    user,
                    assistant,
                    new AppStudioDebugRunSnapshot(TENANT_ID, "5002", "trace-1", NOW, List.of()),
                    result
            );
        }
    }
}
