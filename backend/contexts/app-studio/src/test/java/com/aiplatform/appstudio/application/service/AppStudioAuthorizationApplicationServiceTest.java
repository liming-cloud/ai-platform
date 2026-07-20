package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.GetDebugEvidenceCommand;
import com.aiplatform.appstudio.application.command.PublishAiApplicationCommand;
import com.aiplatform.appstudio.application.command.ReplaceApplicationAccessPolicyCommand;
import com.aiplatform.appstudio.application.command.RunPublishPreflightCommand;
import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.debug.DebugEvidenceView;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.AppPublishPreflightRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeBaseAccessCheckPort;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.appstudio.domain.release.AiApplicationRelease;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation;
import com.aiplatform.appstudio.presentation.permission.AppStudioPermissionCatalog;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCitation;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.model.ResourceAclSubject;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import com.aiplatform.shared.security.port.ResourceAclCommandPort;
import com.aiplatform.shared.security.port.ResourceAudienceQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AppStudioAuthorizationApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-07-10T03:00:00Z");

    @Test
    void concurrentPublishLoserLocksBeforeCanonicalReplayAndSkipsPreflightAndMutation() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        AiApplication draft = application();
        AiApplicationRelease canonicalRelease = new AiApplicationRelease(
                "release-lock-winner",
                TENANT_ID,
                "3001",
                2,
                null,
                "concurrent-lock-publish",
                ApplicationReleaseOperation.PUBLISH,
                publishedSnapshot(draft),
                Set.of("1001", "2002"),
                "1001",
                "并发发布",
                "trace-lock-winner",
                NOW
        );
        AtomicBoolean winnerCommitted = new AtomicBoolean();
        doAnswer(invocation -> {
            winnerCommitted.set(true);
            return null;
        }).when(fixture.applications).acquireMutationLock(TENANT_ID, "3001");
        when(fixture.releases.findByRequestId(
                TENANT_ID,
                "3001",
                "concurrent-lock-publish"
        )).thenAnswer(invocation -> winnerCommitted.get()
                ? Optional.of(canonicalRelease)
                : Optional.empty());

        var result = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT_ID,
                "1001",
                "3001",
                "concurrent-lock-publish",
                "并发发布"
        ));

        assertThat(result.activeReleaseId()).isEqualTo("release-lock-winner");
        assertThat(result.activeReleaseVersion()).isEqualTo(2L);
        InOrder lockThenReplay = inOrder(fixture.releases, fixture.applications);
        lockThenReplay.verify(fixture.releases).findByRequestId(
                TENANT_ID,
                "3001",
                "concurrent-lock-publish"
        );
        lockThenReplay.verify(fixture.applications).acquireMutationLock(TENANT_ID, "3001");
        lockThenReplay.verify(fixture.releases).findByRequestId(
                TENANT_ID,
                "3001",
                "concurrent-lock-publish"
        );
        verify(fixture.preflights, never()).findByRequestId(any(), any(), any());
        verify(fixture.preflights, never()).save(any());
        verify(fixture.releases, never()).createAndActivate(any());
        verifyNoInteractions(fixture.auditPublisher);
    }

    @Test
    void samePublishRequestReturnsCanonicalReleaseWhenWinnerCommitsAfterInitialLookup() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        AiApplication draft = application();
        AiApplication winnerSnapshot = publishedSnapshot(draft);
        AiApplicationRelease canonicalRelease = new AiApplicationRelease(
                "release-winner",
                TENANT_ID,
                "3001",
                2,
                null,
                "concurrent-publish",
                ApplicationReleaseOperation.PUBLISH,
                winnerSnapshot,
                Set.of("1001", "2002"),
                "1001",
                "并发发布",
                "trace-winner",
                NOW
        );
        when(fixture.applications.findById(TENANT_ID, "3001")).thenReturn(
                Optional.of(draft),
                Optional.of(draft),
                Optional.of(winnerSnapshot)
        );
        when(fixture.releases.findByRequestId(TENANT_ID, "3001", "concurrent-publish")).thenReturn(
                Optional.empty(),
                Optional.of(canonicalRelease)
        );

        var result = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT_ID,
                "1001",
                "3001",
                "concurrent-publish",
                "并发发布"
        ));

        assertThat(result.activeReleaseId()).isEqualTo("release-winner");
        assertThat(result.activeReleaseVersion()).isEqualTo(2L);
        verify(fixture.releases, never()).createAndActivate(any());
        verifyNoInteractions(fixture.auditPublisher);
    }

    @Test
    void samePublishRequestReturnsCanonicalReleaseWhenWinnerCommitsBeforePreflightReplay() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        fixture.service.runPublishPreflight(new RunPublishPreflightCommand(
                TENANT_ID,
                "1001",
                "3001",
                "concurrent-before-preflight"
        ));
        ArgumentCaptor<AppPublishPreflightResult> savedPreflight =
                ArgumentCaptor.forClass(AppPublishPreflightResult.class);
        verify(fixture.preflights).save(savedPreflight.capture());

        AiApplication winnerSnapshot = publishedApplication();
        AiApplicationRelease canonicalRelease = new AiApplicationRelease(
                "release-preflight-winner",
                TENANT_ID,
                "3001",
                2,
                null,
                "concurrent-before-preflight",
                ApplicationReleaseOperation.PUBLISH,
                winnerSnapshot,
                Set.of("1001", "2002"),
                "1001",
                "并发发布",
                "trace-preflight-winner",
                NOW
        );
        when(fixture.applications.findById(TENANT_ID, "3001")).thenReturn(Optional.of(winnerSnapshot));
        when(fixture.preflights.findByRequestId(
                TENANT_ID,
                "3001",
                "concurrent-before-preflight"
        )).thenReturn(Optional.of(savedPreflight.getValue()));
        when(fixture.releases.findByRequestId(
                TENANT_ID,
                "3001",
                "concurrent-before-preflight"
        )).thenReturn(Optional.empty(), Optional.of(canonicalRelease));

        var result = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT_ID,
                "1001",
                "3001",
                "concurrent-before-preflight",
                "并发发布"
        ));

        assertThat(result.activeReleaseId()).isEqualTo("release-preflight-winner");
        assertThat(result.activeReleaseVersion()).isEqualTo(2L);
        verify(fixture.releases, never()).createAndActivate(any());
        verifyNoInteractions(fixture.auditPublisher);
    }

    @Test
    void preflightBlocksInvisibleKnowledgeBaseAndPublishesSecurityAudit() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        when(fixture.knowledgeAccess.check(any())).thenAnswer(invocation -> {
            var request = (com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest) invocation.getArgument(0);
            return new KnowledgeBaseAccessCheckResponse(List.of(
                    "1001".equals(request.principalId())
                            ? KnowledgeBaseAccessCheckItem.allowed("20")
                            : KnowledgeBaseAccessCheckItem.denied("20", "resource_not_visible")
            ));
        });

        var result = fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-1")
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.blockers()).extracting(issue -> issue.code())
                .containsExactly("knowledge_base_not_visible");
        verify(fixture.applications, never()).save(any());
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(fixture.auditPublisher).publish(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("SECURITY_APP_PUBLISH_BLOCKED");
    }

    @Test
    void preflightIncludesUsersExpandedFromApplicationResourceAcl() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        when(fixture.audience.findAllowedUserIds(any(), any(), any(), any())).thenReturn(Set.of("1003"));
        when(fixture.permissionCheck.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        when(fixture.knowledgeAccess.check(any())).thenAnswer(invocation -> {
            var request = (com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest) invocation.getArgument(0);
            return new KnowledgeBaseAccessCheckResponse(List.of(
                    "1003".equals(request.principalId())
                            ? KnowledgeBaseAccessCheckItem.denied("20", "resource_not_visible")
                            : KnowledgeBaseAccessCheckItem.allowed("20")
            ));
        });

        var result = fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-acl-audience")
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.blockers()).extracting(issue -> issue.code())
                .contains("knowledge_base_not_visible");
    }

    @Test
    void publishRejectsAStoredPreflightWhenTheAclAudienceChangesAfterItWasChecked() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        var storedPreflight = new java.util.concurrent.atomic.AtomicReference<
                com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult>();
        var currentAudience = new java.util.concurrent.atomic.AtomicReference<>(Set.<String>of());
        when(fixture.preflights.findByRequestId(TENANT_ID, "3001", "publish-audience-change"))
                .thenAnswer(ignored -> Optional.ofNullable(storedPreflight.get()));
        when(fixture.preflights.save(any())).thenAnswer(invocation -> {
            com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult saved = invocation.getArgument(0);
            storedPreflight.set(saved);
            return saved;
        });
        when(fixture.audience.findAllowedUserIds(any(), any(), any(), any()))
                .thenAnswer(ignored -> currentAudience.get());
        when(fixture.permissionCheck.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        when(fixture.knowledgeAccess.check(any())).thenReturn(new KnowledgeBaseAccessCheckResponse(List.of(
                KnowledgeBaseAccessCheckItem.allowed("20")
        )));
        when(fixture.releases.createAndActivate(any())).thenAnswer(invocation -> {
            com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository.MutationCommand command =
                    invocation.getArgument(0);
            var release = new com.aiplatform.appstudio.domain.release.AiApplicationRelease(
                    "release-after-stale-preflight",
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
            );
            return new com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository.MutationResult(
                    release,
                    true
            );
        });

        var preflight = fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-audience-change")
        );
        assertThat(preflight.passed()).isTrue();
        assertThat(fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-audience-change")
        )).isEqualTo(preflight);
        verify(fixture.preflights, org.mockito.Mockito.times(1)).save(any());

        currentAudience.set(Set.of("1003"));

        assertThatThrownBy(() -> fixture.service.publishApplication(
                new com.aiplatform.appstudio.application.command.PublishAiApplicationCommand(
                        TENANT_ID,
                        "1001",
                        "3001",
                        "publish-audience-change",
                        "权限审批后重新发布"
                )
        ))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.PUBLISH_BLOCKED.code());
        verify(fixture.releases, never()).createAndActivate(any());
    }

    @Test
    void preflightRejectsReuseWhenTheDraftModelBindingChangesWithoutTimestampAdvance() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        var storedPreflight = new java.util.concurrent.atomic.AtomicReference<
                com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult>();
        var currentApplication = new java.util.concurrent.atomic.AtomicReference<>(application());
        when(fixture.applications.findById(TENANT_ID, "3001"))
                .thenAnswer(ignored -> Optional.of(currentApplication.get()));
        when(fixture.preflights.findByRequestId(TENANT_ID, "3001", "publish-model-change"))
                .thenAnswer(ignored -> Optional.ofNullable(storedPreflight.get()));
        when(fixture.preflights.save(any())).thenAnswer(invocation -> {
            com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult saved = invocation.getArgument(0);
            storedPreflight.set(saved);
            return saved;
        });

        var originalPreflight = fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-model-change")
        );
        assertThat(originalPreflight.passed()).isTrue();
        AiApplication original = currentApplication.get();
        currentApplication.set(new AiApplication(
                original.tenantId(), original.applicationId(), original.type(), original.name(),
                original.description(), original.modelConfigId(), original.knowledgeBaseIds(), original.toolIds(),
                original.visiblePrincipalIds(), original.systemPrompt(), original.publicationStatus(),
                original.createdAt(), original.updatedAt().plusSeconds(30)
        ));
        assertThat(fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-model-change")
        )).isEqualTo(originalPreflight);

        original = currentApplication.get();
        currentApplication.set(new AiApplication(
                original.tenantId(), original.applicationId(), original.type(), original.name(),
                original.description(), "7", original.knowledgeBaseIds(), original.toolIds(),
                original.visiblePrincipalIds(), original.systemPrompt(), original.publicationStatus(),
                original.createdAt(), original.updatedAt()
        ));

        assertThatThrownBy(() -> fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-model-change")
        ))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.PUBLISH_BLOCKED.code())
                .hasMessageContaining("新的 publishRequestId");
        verify(fixture.preflights, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    void preflightRejectsTheCanonicalRowWhenAConcurrentRequestBoundTheIdToAnotherCandidate() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        when(fixture.preflights.save(any())).thenAnswer(invocation -> {
            com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult attempted =
                    invocation.getArgument(0);
            return new com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult(
                    attempted.tenantId(),
                    attempted.applicationId(),
                    attempted.publishRequestId(),
                    "c".repeat(64),
                    attempted.passed(),
                    attempted.blockers(),
                    attempted.warnings(),
                    attempted.traceId(),
                    attempted.checkedBy(),
                    attempted.checkedAt()
            );
        });

        assertThatThrownBy(() -> fixture.service.runPublishPreflight(
                new RunPublishPreflightCommand(TENANT_ID, "1001", "3001", "publish-race")
        ))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.PUBLISH_BLOCKED.code())
                .hasMessageContaining("其他发布候选");
    }

    @Test
    void applicationOwnerReplacesUseAclAndPublishesAuditedReason() {
        Fixture fixture = new Fixture(Set.of(AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL));

        var result = fixture.service.replaceApplicationAccessPolicy(
                new ReplaceApplicationAccessPolicyCommand(
                        TENANT_ID,
                        "1001",
                        "3001",
                        Set.of("1003"),
                        Set.of("3003"),
                        "授权客服团队使用"
                )
        );

        InOrder mutationOrder = inOrder(fixture.applications, fixture.resourceAclCommands);
        mutationOrder.verify(fixture.applications).acquireMutationLock(TENANT_ID, "3001");
        mutationOrder.verify(fixture.applications).findById(TENANT_ID, "3001");
        mutationOrder.verify(fixture.resourceAclCommands).replaceAllowingSubjects(
                TENANT_ID,
                "AI_APPLICATION",
                "3001",
                AppStudioPermissionCatalog.APPLICATION_USE,
                Set.of(new ResourceAclSubject("user", "1003"), new ResourceAclSubject("role", "3003")),
                "1001"
        );
        mutationOrder.verify(fixture.applications).save(any());
        verify(fixture.resourceAclCommands).replaceAllowingSubjects(
                TENANT_ID,
                "AI_APPLICATION",
                "3001",
                AppStudioPermissionCatalog.APPLICATION_USE,
                Set.of(new ResourceAclSubject("user", "1003"), new ResourceAclSubject("role", "3003")),
                "1001"
        );
        assertThat(result.authorizedUserCount()).isEqualTo(1);
        assertThat(result.authorizedRoleCount()).isEqualTo(1);
        ArgumentCaptor<AiApplication> updatedApplication = ArgumentCaptor.forClass(AiApplication.class);
        verify(fixture.applications).save(updatedApplication.capture());
        assertThat(updatedApplication.getValue().updatedAt()).isEqualTo(NOW);
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(fixture.auditPublisher).publish(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("APP_APPLICATION_ACCESS_POLICY_REPLACED");
        assertThat(audit.getValue().detailAttributes()).containsEntry("reason", "授权客服团队使用");
    }

    @Test
    void authorizedBusinessUserReadsOnlyMaskedOwnDebugEvidence() {
        Fixture fixture = new Fixture(Set.of(AppStudioPermissionCatalog.APPLICATION_USE));
        when(fixture.applications.findById(TENANT_ID, "3001")).thenReturn(Optional.of(publishedApplication()));
        when(fixture.permissionCheck.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        when(fixture.debugQueries.findDebugRunDetail(TENANT_ID, "3001", "1003", "5002"))
                .thenReturn(Optional.of(debugDetail()));

        var result = fixture.service.getDebugEvidence(new GetDebugEvidenceCommand(
                TENANT_ID, "1003", "3001", "5002", DebugEvidenceView.SUMMARY, null
        ));

        assertThat(result.masked()).isTrue();
        verify(fixture.debugQueries).findDebugRunDetail(TENANT_ID, "3001", "1003", "5002");
    }

    @Test
    void rejectsOtherUsersSessionWithResourceNotVisibleAndAudit() {
        Fixture fixture = new Fixture(Set.of(AppStudioPermissionCatalog.APPLICATION_USE));
        when(fixture.sessions.findById(TENANT_ID, "4001")).thenReturn(Optional.of(
                new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "2002", "他人会话", NOW, NOW)
        ));

        assertThatThrownBy(() -> fixture.service.listMessages(TENANT_ID, "1001", "4001"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(fixture.auditPublisher).publish(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("SECURITY_APP_SESSION_ACCESS_DENIED");
        assertThat(audit.getValue().detailAttributes()).containsEntry("reason", "session_owner_mismatch");
    }

    @Test
    void rejectsDuplicateClientMessageBeforeRetrievalOrModelInvocation() {
        Fixture fixture = new Fixture(Set.of(AppStudioPermissionCatalog.APPLICATION_USE));
        when(fixture.sessions.findById(TENANT_ID, "4001")).thenReturn(Optional.of(
                new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "1001", "我的会话", NOW, NOW)
        ));
        when(fixture.sessions.existsByClientMessageId(TENANT_ID, "4001", "client-1")).thenReturn(true);

        assertThatThrownBy(() -> fixture.service.sendMessage(new SendChatMessageCommand(
                TENANT_ID, "1001", "4001", "client-1", "问题", "trace-1"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("重复消息请求");
        verifyNoInteractions(fixture.retrieval, fixture.completion);
    }

    @Test
    void modelPolicyDenialUsesServerAuthorizationSnapshotAndWritesNothing() {
        Fixture fixture = new Fixture(Set.of(
                AppStudioPermissionCatalog.APPLICATION_USE,
                AppStudioPermissionCatalog.MODEL_USE
        ));
        when(fixture.sessions.findById(TENANT_ID, "4001")).thenReturn(Optional.of(
                new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "1001", "我的会话", NOW, NOW)
        ));
        when(fixture.retrieval.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(
                true,
                List.of(new KnowledgeRetrievalCitation("20", "30", "1", "文档", "片段", "p1", 0.9)),
                List.of(new KnowledgeRetrievalEvidence("20", "30", "1", 0.9, "VECTOR", "v1")),
                null,
                0
        ));
        when(fixture.modelPolicy.decide(any())).thenReturn(new AppModelUsageDecision(
                false, "disabled", "模型策略已禁用", null, Map.of()
        ));

        assertThatThrownBy(() -> fixture.service.sendMessage(new SendChatMessageCommand(
                TENANT_ID, "1001", "4001", "client-2", "问题", "trace-2"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.MODEL_POLICY_DENIED.code());
        verify(fixture.sessions, never()).appendMessage(any());
        verifyNoInteractions(fixture.completion);
        ArgumentCaptor<AppModelUsageRequest> request = ArgumentCaptor.forClass(AppModelUsageRequest.class);
        verify(fixture.modelPolicy).decide(request.capture());
        assertThat(request.getValue().roleCodes()).containsExactly("app-admin");
        assertThat(request.getValue().permissionSnapshot()).contains(AppStudioPermissionCatalog.MODEL_USE);
        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(fixture.auditPublisher).publish(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("SECURITY_APP_MODEL_POLICY_DENIED");
    }

    @Test
    void summaryDebugEvidenceIsOwnerScopedAndMarkedForMasking() {
        Fixture fixture = new Fixture(Set.of(AppStudioPermissionCatalog.APPLICATION_USE));
        when(fixture.debugQueries.findDebugRunDetail(TENANT_ID, "3001", "1001", "5002"))
                .thenReturn(Optional.of(debugDetail()));

        var result = fixture.service.getDebugEvidence(new GetDebugEvidenceCommand(
                TENANT_ID, "1001", "3001", "5002", DebugEvidenceView.SUMMARY, null
        ));

        assertThat(result.masked()).isTrue();
        assertThat(result.auditEventId()).isNull();
        verify(fixture.debugQueries).findDebugRunDetail(TENANT_ID, "3001", "1001", "5002");
    }

    @Test
    void fullDebugEvidenceRequiresDedicatedPermissionAndIsAudited() {
        Fixture denied = new Fixture(Set.of(AppStudioPermissionCatalog.APPLICATION_READ));
        assertThatThrownBy(() -> denied.service.getDebugEvidence(new GetDebugEvidenceCommand(
                TENANT_ID, "1001", "3001", "5002", DebugEvidenceView.FULL, "排查失败"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.AUTHORIZATION_DENIED.code());
        ArgumentCaptor<AuditEvent> deniedAudit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(denied.auditPublisher).publish(deniedAudit.capture());
        assertThat(deniedAudit.getValue().action()).isEqualTo("SECURITY_APP_DEBUG_EVIDENCE_DENIED");

        Fixture debugOnly = new Fixture(Set.of(AppStudioPermissionCatalog.DEBUG_EVIDENCE_READ));
        assertThatThrownBy(() -> debugOnly.service.getDebugEvidence(new GetDebugEvidenceCommand(
                TENANT_ID, "1001", "3001", "5002", DebugEvidenceView.FULL, "排查失败"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.AUTHORIZATION_DENIED.code());

        Fixture allowed = new Fixture(Set.of(
                AppStudioPermissionCatalog.DEBUG_EVIDENCE_READ,
                AppStudioPermissionCatalog.APPLICATION_READ
        ));
        when(allowed.debugQueries.findDebugRunDetail(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq("3001"),
                isNull(),
                org.mockito.ArgumentMatchers.eq("5002")
        )).thenReturn(Optional.of(debugDetail()));
        var result = allowed.service.getDebugEvidence(new GetDebugEvidenceCommand(
                TENANT_ID, "1001", "3001", "5002", DebugEvidenceView.FULL, "排查失败"
        ));

        assertThat(result.masked()).isFalse();
        assertThat(result.auditEventId()).isNotBlank();
        ArgumentCaptor<AuditEvent> viewedAudit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(allowed.auditPublisher).publish(viewedAudit.capture());
        assertThat(viewedAudit.getValue().action()).isEqualTo("SECURITY_APP_DEBUG_EVIDENCE_VIEWED");
        assertThat(viewedAudit.getValue().detailAttributes()).containsEntry("reason", "排查失败");
    }

    private static final class Fixture {
        private final AiApplicationRepository applications = mock(AiApplicationRepository.class);
        private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        private final KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        private final KnowledgeBaseAccessCheckPort knowledgeAccess = mock(KnowledgeBaseAccessCheckPort.class);
        private final ModelUsagePolicyPort modelPolicy = mock(ModelUsagePolicyPort.class);
        private final ChatCompletionPort completion = mock(ChatCompletionPort.class);
        private final AppStudioDebugRunSnapshotPort debugSnapshots = mock(AppStudioDebugRunSnapshotPort.class);
        private final AppStudioDebugRunQueryPort debugQueries = mock(AppStudioDebugRunQueryPort.class);
        private final AppPublishPreflightRepository preflights = mock(AppPublishPreflightRepository.class);
        private final com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository releases =
                mock(com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository.class);
        private final AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        private final PermissionCheckPort permissionCheck = mock(PermissionCheckPort.class);
        private final ResourceAclCommandPort resourceAclCommands = mock(ResourceAclCommandPort.class);
        private final ResourceAudienceQueryPort audience = mock(ResourceAudienceQueryPort.class);
        private final AppStudioApplicationService service;

        private Fixture(Set<PermissionCode> permissions) {
            RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
            when(authorization.findPermissionCodes(any(), any(), any())).thenReturn(permissions);
            when(authorization.findRoleCodes(any(), any(), any())).thenReturn(Set.of("app-admin"));
            when(applications.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
            when(knowledgeAccess.check(any())).thenReturn(new KnowledgeBaseAccessCheckResponse(List.of(
                    KnowledgeBaseAccessCheckItem.allowed("20")
            )));
            when(modelPolicy.decide(any())).thenReturn(allowedModelDecision());
            when(preflights.findByRequestId(any(), any(), any())).thenReturn(Optional.empty());
            when(preflights.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(audience.findAllowedUserIds(any(), any(), any(), any())).thenReturn(Set.of());
            when(releases.findActive(any(), any())).thenAnswer(invocation -> {
                TenantId tenantId = invocation.getArgument(0);
                String applicationId = invocation.getArgument(1);
                return applications.findById(tenantId, applicationId)
                        .map(application -> {
                            Set<String> allowedUsers = new java.util.LinkedHashSet<>(
                                    application.visiblePrincipalIds()
                            );
                            if (permissionCheck.canAccess(
                                    tenantId,
                                    "1003",
                                    AppStudioPermissionCatalog.APPLICATION_USE,
                                    "AI_APPLICATION",
                                    applicationId
                            )) {
                                allowedUsers.add("1003");
                            }
                            return new com.aiplatform.appstudio.domain.release.AiApplicationRelease(
                                    "test-release-" + applicationId,
                                    tenantId,
                                    applicationId,
                                    1,
                                    null,
                                    "test-request-" + applicationId,
                                    com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation.PUBLISH,
                                    publishedSnapshot(application),
                                    allowedUsers,
                                    application.visiblePrincipalIds().iterator().next(),
                                    "测试发布",
                                    "test-trace",
                                    application.updatedAt()
                            );
                        });
            });
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            service = new AppStudioApplicationService(
                    applications,
                    releases,
                    sessions,
                    retrieval,
                    knowledgeAccess,
                    modelPolicy,
                    completion,
                    AppStudioApplicationServiceTestFactory.chatRunTransactions(sessions, debugSnapshots, clock),
                    debugQueries,
                    permissionCheck,
                    authorization,
                    resourceAclCommands,
                    audience,
                    preflights,
                    auditPublisher,
                    clock
            );
        }
    }

    private static AiApplication application() {
        return new AiApplication(
                TENANT_ID, "3001", AiApplicationType.KNOWLEDGE_QA, "知识助手", "知识助手描述", "6",
                List.of("20"), List.of(), Set.of("1001", "2002"), "请基于知识回答",
                ApplicationPublicationStatus.DRAFT, NOW, NOW
        );
    }

    private static AiApplication publishedApplication() {
        return publishedSnapshot(application());
    }

    private static AiApplication publishedSnapshot(AiApplication application) {
        return new AiApplication(
                application.tenantId(),
                application.applicationId(),
                application.type(),
                application.name(),
                application.description(),
                application.modelConfigId(),
                application.knowledgeBaseIds(),
                application.toolIds(),
                application.visiblePrincipalIds(),
                application.systemPrompt(),
                ApplicationPublicationStatus.PUBLISHED,
                application.createdAt(),
                application.updatedAt()
        );
    }

    private static AppModelUsageDecision allowedModelDecision() {
        return new AppModelUsageDecision(
                true,
                "allowed",
                "策略允许",
                new AppModelRuntimeDescriptor(
                        "6", "3001", "provider", "openai-compatible",
                        new AppModelRuntimeConnectionRef("https://provider.example/v1", "api_key", "secret-test"),
                        "chat-model", 7, "CHAT", Set.of("chat-completion"), Map.of(), "a".repeat(64), "v1"
                ),
                Map.of("policy", "test")
        );
    }

    private static AppStudioDebugRunDetail debugDetail() {
        return new AppStudioDebugRunDetail(
                "5002", "5001", "3001", "4001", "trace-1", "90", "SUCCEEDED", true,
                "敏感用户问题", "敏感回答", null, List.of(), List.of(),
                "provider", "chat-model", "spring-ai-chat-completion",
                20, 10, 100, "APP_STUDIO_CHAT", "AI_APPLICATION", "3001", null, null, NOW
        );
    }
}
