package com.aiplatform.appstudio.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.AppPublishPreflightRepository;
import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.appstudio.domain.release.AiApplicationRelease;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import com.aiplatform.shared.security.port.ResourceAclCommandPort;
import com.aiplatform.shared.security.port.ResourceAudienceQueryPort;
import java.time.Clock;

final class AppStudioApplicationServiceTestFactory {
    private AppStudioApplicationServiceTestFactory() {
    }

    static AppStudioApplicationService create(
            AiApplicationRepository aiApplicationRepository,
            ChatSessionRepository chatSessionRepository,
            KnowledgeRetrievalPort knowledgeRetrievalPort,
            ModelUsagePolicyPort modelUsagePolicyPort,
            ChatCompletionPort chatCompletionPort,
            AppStudioDebugRunSnapshotPort debugRunSnapshotPort,
            AppStudioDebugRunQueryPort debugRunQueryPort,
            PermissionCheckPort permissionCheckPort,
            RbacAuthorizationQueryPort authorizationQueryPort,
            Clock clock
    ) {
        ChatRunTransactionService chatRuns = chatRunTransactions(
                chatSessionRepository, debugRunSnapshotPort, clock
        );
        return createWithChatRuns(
                aiApplicationRepository,
                chatSessionRepository,
                knowledgeRetrievalPort,
                modelUsagePolicyPort,
                chatCompletionPort,
                chatRuns,
                debugRunQueryPort,
                permissionCheckPort,
                authorizationQueryPort,
                clock
        );
    }

    static AppStudioApplicationService createWithChatRuns(
            AiApplicationRepository aiApplicationRepository,
            ChatSessionRepository chatSessionRepository,
            KnowledgeRetrievalPort knowledgeRetrievalPort,
            ModelUsagePolicyPort modelUsagePolicyPort,
            ChatCompletionPort chatCompletionPort,
            ChatRunTransactionService chatRuns,
            AppStudioDebugRunQueryPort debugRunQueryPort,
            PermissionCheckPort permissionCheckPort,
            RbacAuthorizationQueryPort authorizationQueryPort,
            Clock clock
    ) {
        AppPublishPreflightRepository preflights = mock(AppPublishPreflightRepository.class);
        when(preflights.findByRequestId(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(preflights.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new AppStudioApplicationService(
                aiApplicationRepository,
                compatibilityReleaseRepository(aiApplicationRepository, permissionCheckPort),
                chatSessionRepository,
                knowledgeRetrievalPort,
                request -> new KnowledgeBaseAccessCheckResponse(request.knowledgeBaseIds().stream()
                        .map(KnowledgeBaseAccessCheckItem::allowed)
                        .toList()),
                modelUsagePolicyPort,
                chatCompletionPort,
                chatRuns,
                debugRunQueryPort,
                permissionCheckPort,
                authorizationQueryPort,
                mock(ResourceAclCommandPort.class),
                emptyAudience(),
                preflights,
                mock(AuditEventPublisher.class),
                clock
        );
    }

    static AppStudioApplicationService createWithReleases(
            AiApplicationRepository aiApplicationRepository,
            ChatSessionRepository chatSessionRepository,
            KnowledgeRetrievalPort knowledgeRetrievalPort,
            ModelUsagePolicyPort modelUsagePolicyPort,
            ChatCompletionPort chatCompletionPort,
            AppStudioDebugRunSnapshotPort debugRunSnapshotPort,
            AppStudioDebugRunQueryPort debugRunQueryPort,
            PermissionCheckPort permissionCheckPort,
            RbacAuthorizationQueryPort authorizationQueryPort,
            ResourceAudienceQueryPort audience,
            ApplicationReleaseRepository releases,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        AppPublishPreflightRepository preflights = mock(AppPublishPreflightRepository.class);
        when(preflights.findByRequestId(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(preflights.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new AppStudioApplicationService(
                aiApplicationRepository,
                releases,
                chatSessionRepository,
                knowledgeRetrievalPort,
                request -> new KnowledgeBaseAccessCheckResponse(request.knowledgeBaseIds().stream()
                        .map(KnowledgeBaseAccessCheckItem::allowed)
                        .toList()),
                modelUsagePolicyPort,
                chatCompletionPort,
                chatRunTransactions(chatSessionRepository, debugRunSnapshotPort, clock),
                debugRunQueryPort,
                permissionCheckPort,
                authorizationQueryPort,
                mock(ResourceAclCommandPort.class),
                audience,
                preflights,
                auditPublisher,
                clock
        );
    }

    static ChatRunTransactionService chatRunTransactions(
            ChatSessionRepository chatSessionRepository,
            AppStudioDebugRunSnapshotPort debugRunSnapshotPort,
            Clock clock
    ) {
        ChatRunRepository runs = mock(ChatRunRepository.class);
        when(runs.claim(any())).thenAnswer(invocation -> {
            ChatRunRepository.ClaimRequest request = invocation.getArgument(0);
            return ChatRunRepository.Claim.acquired(new ChatRunRepository.Ownership(
                    request.tenantId(),
                    "9901",
                    request.sessionId(),
                    request.applicationId(),
                    request.actorId(),
                    request.clientMessageId(),
                    request.leaseOwner(),
                    1
            ));
        });
        return new ChatRunTransactionService(runs, chatSessionRepository, debugRunSnapshotPort, clock);
    }

    private static ResourceAudienceQueryPort emptyAudience() {
        ResourceAudienceQueryPort audience = mock(ResourceAudienceQueryPort.class);
        when(audience.findAllowedUserIds(any(), any(), any(), any())).thenReturn(java.util.Set.of());
        return audience;
    }

    private static ApplicationReleaseRepository compatibilityReleaseRepository(
            AiApplicationRepository applications,
            PermissionCheckPort permissionCheckPort
    ) {
        ApplicationReleaseRepository releases = mock(ApplicationReleaseRepository.class);
        when(releases.findByRequestId(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(releases.findById(any(), any(), any())).thenReturn(java.util.Optional.empty());
        when(releases.findAll(any(), any())).thenReturn(java.util.List.of());
        when(releases.findActive(any(), any())).thenAnswer(invocation -> {
            com.aiplatform.shared.kernel.TenantId tenantId = invocation.getArgument(0);
            String applicationId = invocation.getArgument(1);
            return applications.findById(tenantId, applicationId)
                    .filter(application -> application.publicationStatus()
                            == com.aiplatform.appstudio.domain.ApplicationPublicationStatus.PUBLISHED)
                    .map(application -> new AiApplicationRelease(
                            "test-release-" + applicationId,
                            tenantId,
                            applicationId,
                            1,
                            null,
                            "test-active-" + applicationId,
                            com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation.PUBLISH,
                            application,
                            compatibilityAudience(tenantId, applicationId, application, permissionCheckPort),
                            application.visiblePrincipalIds().iterator().next(),
                            "测试兼容发布",
                            "test-trace",
                            application.updatedAt()
                    ));
        });
        when(releases.findActiveByApplicationIds(any(), any())).thenAnswer(invocation -> {
            com.aiplatform.shared.kernel.TenantId tenantId = invocation.getArgument(0);
            java.util.List<String> applicationIds = invocation.getArgument(1);
            java.util.Map<String, AiApplicationRelease> active = new java.util.LinkedHashMap<>();
            for (String applicationId : applicationIds) {
                releases.findActive(tenantId, applicationId)
                        .ifPresent(release -> active.put(applicationId, release));
            }
            return java.util.Map.copyOf(active);
        });
        when(releases.createAndActivate(any())).thenAnswer(invocation -> {
            ApplicationReleaseRepository.MutationCommand command = invocation.getArgument(0);
            applications.save(command.snapshot());
            return new ApplicationReleaseRepository.MutationResult(
                    new AiApplicationRelease(
                            "test-release-" + command.applicationId(),
                            command.tenantId(),
                            command.applicationId(),
                            1,
                            command.sourceReleaseId(),
                            command.requestId(),
                            command.operation(),
                            command.snapshot(),
                            command.allowedUserIds(),
                            command.actorId(),
                            command.reason(),
                            command.traceId(),
                            command.occurredAt()
                    ),
                    true
            );
        });
        return releases;
    }

    private static java.util.Set<String> compatibilityAudience(
            com.aiplatform.shared.kernel.TenantId tenantId,
            String applicationId,
            com.aiplatform.appstudio.domain.AiApplication application,
            PermissionCheckPort permissionCheckPort
    ) {
        java.util.Set<String> audience = new java.util.LinkedHashSet<>(application.visiblePrincipalIds());
        for (String userId : java.util.List.of("1001", "1002", "1003", "2002")) {
            if (permissionCheckPort.canAccess(
                    tenantId,
                    userId,
                    new com.aiplatform.shared.security.model.PermissionCode("app-studio:application:use"),
                    "AI_APPLICATION",
                    applicationId
            )) {
                audience.add(userId);
            }
        }
        return java.util.Set.copyOf(audience);
    }
}
