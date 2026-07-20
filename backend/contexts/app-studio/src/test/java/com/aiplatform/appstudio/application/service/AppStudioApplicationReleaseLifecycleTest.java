package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.CreateChatSessionCommand;
import com.aiplatform.appstudio.application.command.PublishAiApplicationCommand;
import com.aiplatform.appstudio.application.command.ReplaceApplicationAccessPolicyCommand;
import com.aiplatform.appstudio.application.command.RollbackApplicationReleaseCommand;
import com.aiplatform.appstudio.application.command.RunPublishPreflightCommand;
import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.command.UpdateAiApplicationCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionRequest;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionResult;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.appstudio.domain.release.AiApplicationRelease;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCitation;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import com.aiplatform.shared.security.port.ResourceAudienceQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AppStudioApplicationReleaseLifecycleTest {
    private static final TenantId TENANT = TenantId.of("1");
    private static final Instant NOW = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void activeRuntimeIsImmutableAcrossDraftEditPublishAndRollback() {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版提示词", "9001", NOW.minusSeconds(60)));

        var first = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-v1", "首次上线"
        ));
        fixture.service.updateApplication(new UpdateAiApplicationCommand(
                TENANT, "1001", "3001", "制度助手 v2", "第二版草稿",
                "9002", List.of("21"), "第二版提示词"
        ));

        assertThat(fixture.service.listApplications(TENANT, "1001"))
                .singleElement()
                .satisfies(catalog -> {
                    var application = catalog.application();
                    assertThat(application.systemPrompt()).isEqualTo("第二版提示词");
                    assertThat(application.modelConfigId()).isEqualTo("9002");
                    assertThat(application.activeReleaseId()).isEqualTo(first.activeReleaseId());
                    assertThat(application.activeReleaseVersion()).isEqualTo(1L);
                });

        var second = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-v2", "发布第二版"
        ));
        var rollback = fixture.service.rollbackApplication(new RollbackApplicationReleaseCommand(
                TENANT, "1001", "3001", first.activeReleaseId(), "rollback-v1", "第二版不符合预期"
        ));

        assertThat(second.activeReleaseVersion()).isEqualTo(2L);
        assertThat(rollback.activeReleaseVersion()).isEqualTo(3L);
        assertThat(rollback.activeReleaseOperation()).isEqualTo("ROLLBACK");
        assertThat(rollback.sourceReleaseId()).isEqualTo(first.activeReleaseId());
        assertThat(rollback.sourceReleaseVersion()).isEqualTo(1L);
        assertThat(rollback.systemPrompt()).isEqualTo("第一版提示词");
        assertThat(fixture.service.listApplications(TENANT, "1001"))
                .singleElement()
                .satisfies(catalog -> {
                    var ownerDraft = catalog.application();
                    assertThat(ownerDraft.systemPrompt()).isEqualTo("第二版提示词");
                    assertThat(ownerDraft.activeReleaseId()).isEqualTo(rollback.activeReleaseId());
                    assertThat(ownerDraft.activeReleaseVersion()).isEqualTo(3L);
                    assertThat(ownerDraft.activeReleaseOperation()).isEqualTo("ROLLBACK");
                    assertThat(ownerDraft.sourceReleaseId()).isEqualTo(first.activeReleaseId());
                    assertThat(ownerDraft.sourceReleaseVersion()).isEqualTo(1L);
                });
        assertThat(fixture.service.listApplications(TENANT, "1002"))
                .singleElement()
                .satisfies(catalog -> {
                    assertThat(catalog.application().systemPrompt()).isNull();
                    assertThat(catalog.application().modelConfigId()).isNull();
                    assertThat(catalog.application().knowledgeBaseIds()).isEmpty();
                });
        assertThat(fixture.service.listApplicationReleases(TENANT, "1001", "3001"))
                .extracting(result -> result.version())
                .containsExactly(3L, 2L, 1L);
        assertThat(fixture.service.listApplicationReleases(TENANT, "1001", "3001"))
                .filteredOn(result -> result.active())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.sourceReleaseId()).isEqualTo(first.activeReleaseId());
                    assertThat(result.sourceReleaseVersion()).isEqualTo(1L);
                    assertThat(result.applicationType()).isEqualTo("KNOWLEDGE_QA");
                    assertThat(result.systemPromptSummary()).isEqualTo("第一版提示词");
                    assertThat(result.systemPromptSha256()).hasSize(64);
                    assertThat(result.ownerUserCount()).isEqualTo(1);
                    assertThat(result.allowedUserCount()).isEqualTo(2);
                    assertThat(result.createdAt()).isEqualTo(NOW);
                    assertThat(result.publishedAt()).isEqualTo(NOW);
                });
        assertThat(fixture.service.getApplicationRelease(
                TENANT,
                "1001",
                "3001",
                first.activeReleaseId()
        )).satisfies(detail -> {
            assertThat(detail.version()).isEqualTo(1L);
            assertThat(detail.active()).isFalse();
            assertThat(detail.systemPrompt()).isEqualTo("第一版提示词");
            assertThat(detail.systemPromptSha256()).hasSize(64);
            assertThat(detail.allowedUserCount()).isEqualTo(2);
        });
        assertThat(fixture.service.getApplicationRelease(
                TENANT,
                "1001",
                "3001",
                rollback.activeReleaseId()
        )).satisfies(detail -> {
            assertThat(detail.active()).isTrue();
            assertThat(detail.sourceReleaseId()).isEqualTo(first.activeReleaseId());
            assertThat(detail.sourceReleaseVersion()).isEqualTo(1L);
        });
    }

    @Test
    void ownerAndBusinessChatsBothResolveTheActiveSnapshotAfterDraftEdits() {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版生产提示词", "9001", NOW.minusSeconds(60)));
        fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-chat-v1", "上线第一版"
        ));
        fixture.service.updateApplication(new UpdateAiApplicationCommand(
                TENANT,
                "1001",
                "3001",
                "第二版草稿",
                "尚未发布的第二版",
                "9002",
                List.of("21"),
                "第二版草稿提示词不得进入流量"
        ));
        org.mockito.Mockito.clearInvocations(fixture.policies, fixture.retrieval, fixture.completion);

        for (String actorId : List.of("1001", "1002")) {
            fixture.service.createSession(new CreateChatSessionCommand(
                    TENANT,
                    actorId,
                    "3001",
                    "active release 验证",
                    "session-" + actorId
            ));
            fixture.service.sendMessage(new SendChatMessageCommand(
                    TENANT,
                    actorId,
                    "4001",
                    "message-" + actorId,
                    "制度内容是什么？",
                    "trace-" + actorId
            ));
        }

        ArgumentCaptor<ChatCompletionRequest> completionRequests = ArgumentCaptor.forClass(
                ChatCompletionRequest.class
        );
        org.mockito.Mockito.verify(fixture.completion, org.mockito.Mockito.times(2))
                .complete(completionRequests.capture());
        assertThat(completionRequests.getAllValues()).allSatisfy(request -> {
            assertThat(request.applicationInstruction()).contains("第一版生产提示词");
            assertThat(request.applicationInstruction()).doesNotContain("第二版草稿提示词不得进入流量");
        });
        ArgumentCaptor<com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest> policyRequests =
                ArgumentCaptor.forClass(
                        com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest.class
                );
        org.mockito.Mockito.verify(fixture.policies, org.mockito.Mockito.times(2)).decide(policyRequests.capture());
        assertThat(policyRequests.getAllValues())
                .extracting(request -> request.requestedModelConfigId())
                .containsOnly("9001");
        ArgumentCaptor<com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest> retrievalRequests =
                ArgumentCaptor.forClass(
                        com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest.class
                );
        org.mockito.Mockito.verify(fixture.retrieval, org.mockito.Mockito.times(2))
                .retrieve(retrievalRequests.capture());
        assertThat(retrievalRequests.getAllValues())
                .allSatisfy(request -> assertThat(request.knowledgeBaseIds()).containsExactly("20"));
    }

    @Test
    void activeReleaseSwitchMakesExistingSessionRecoverablyStaleBeforeAnyRuntimeCall() {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版生产提示词", "9001", NOW.minusSeconds(60)));
        var v1 = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-session-v1", "上线第一版"
        ));
        var session = fixture.service.createSession(new CreateChatSessionCommand(
                TENANT, "1002", "3001", "固定第一版", "session-release-pin"
        ));

        assertThat(session.releaseId()).isEqualTo(v1.activeReleaseId());
        assertThat(session.releaseVersion()).isEqualTo(1L);

        fixture.service.updateApplication(new UpdateAiApplicationCommand(
                TENANT, "1001", "3001", "第二版", "第二版", "9002", List.of("21"), "第二版生产提示词"
        ));
        var v2 = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-session-v2", "切换第二版"
        ));
        Mockito.clearInvocations(fixture.policies, fixture.retrieval, fixture.completion);

        assertThatThrownBy(() -> fixture.service.sendMessage(new SendChatMessageCommand(
                TENANT,
                "1002",
                session.sessionId(),
                "stale-session-message",
                "旧会话不得漂移到第二版",
                "trace-stale-session"
        )))
                .isInstanceOf(com.aiplatform.shared.kernel.ApplicationException.class)
                .satisfies(failure -> assertThat(
                        ((com.aiplatform.shared.kernel.ApplicationException) failure).errorCode()
                ).isEqualTo(AppStudioErrorCodes.SESSION_RELEASE_CHANGED));
        Mockito.verifyNoInteractions(fixture.policies, fixture.retrieval, fixture.completion);

        var replacement = fixture.service.createSession(new CreateChatSessionCommand(
                TENANT, "1002", "3001", "固定第二版", "session-release-pin-v2"
        ));
        assertThat(replacement.releaseId()).isEqualTo(v2.activeReleaseId());
        assertThat(replacement.releaseVersion()).isEqualTo(2L);
    }

    @Test
    void samePublishRequestIsIdempotentEvenAfterDraftChanges() {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版提示词", "9001", NOW.minusSeconds(60)));
        PublishAiApplicationCommand command = new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "same-request", "首次上线"
        );

        var first = fixture.service.publishApplication(command);
        fixture.service.updateApplication(new UpdateAiApplicationCommand(
                TENANT, "1001", "3001", "新草稿", "新草稿", "9002", List.of("21"), "不得被重复发布"
        ));
        var duplicate = fixture.service.publishApplication(command);

        assertThat(duplicate.activeReleaseId()).isEqualTo(first.activeReleaseId());
        assertThat(duplicate.activeReleaseVersion()).isEqualTo(1L);
        assertThat(fixture.releases.findAll(TENANT, "3001")).hasSize(1);
    }

    @Test
    void reusingAnIdempotencyKeyForAnotherOperationOrRollbackTargetIsRejected() {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版提示词", "9001", NOW.minusSeconds(60)));
        var first = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-v1-key", "发布第一版"
        ));
        fixture.service.updateApplication(new UpdateAiApplicationCommand(
                TENANT, "1001", "3001", "第二版", "第二版", "9002", List.of("21"), "第二版提示词"
        ));
        var second = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-v2-key", "发布第二版"
        ));

        assertThatThrownBy(() -> fixture.service.rollbackApplication(new RollbackApplicationReleaseCommand(
                TENANT, "1001", "3001", first.activeReleaseId(), "publish-v1-key", "错误复用发布键"
        )))
                .hasMessageContaining(AppStudioErrorCodes.PUBLISH_BLOCKED.code())
                .hasMessageContaining("幂等请求 ID");

        fixture.service.rollbackApplication(new RollbackApplicationReleaseCommand(
                TENANT, "1001", "3001", first.activeReleaseId(), "rollback-key", "回滚第一版"
        ));
        assertThatThrownBy(() -> fixture.service.rollbackApplication(new RollbackApplicationReleaseCommand(
                TENANT, "1001", "3001", second.activeReleaseId(), "rollback-key", "错误复用至第二版"
        )))
                .hasMessageContaining(AppStudioErrorCodes.PUBLISH_BLOCKED.code())
                .hasMessageContaining("幂等请求 ID");
        assertThat(fixture.releases.findAll(TENANT, "3001")).hasSize(3);
    }

    @Test
    void concurrentSameRequestConvergesOnOneRelease() throws Exception {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版提示词", "9001", NOW.minusSeconds(60)));
        PublishAiApplicationCommand command = new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "concurrent-request", "并发发布"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> publish = () -> {
                ready.countDown();
                start.await();
                return fixture.service.publishApplication(command).activeReleaseId();
            };
            Future<String> first = executor.submit(publish);
            Future<String> second = executor.submit(publish);
            ready.await();
            start.countDown();

            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(fixture.releases.findAll(TENANT, "3001")).hasSize(1);
    }

    @Test
    void businessUserCannotFallBackToMutablePublishedRowWithoutActiveRelease() {
        Fixture fixture = fixture(TENANT, publishedDraft("可变主表提示词", "9001", NOW));

        assertThatThrownBy(() -> fixture.service.createSession(new CreateChatSessionCommand(
                TENANT, "1002", "3001", "业务会话", null
        )))
                .hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
    }

    @Test
    void releaseSnapshotSummaryIsNotVisibleToNonOwnerEvenWhenUsePermissionExists() {
        Fixture fixture = fixture(TENANT, publishedDraft("不得泄漏的发布提示词", "9001", NOW.minusSeconds(60)));
        fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "private-release", "仅发布负责人可审阅"
        ));

        assertThatThrownBy(() -> fixture.service.listApplicationReleases(TENANT, "1002", "3001"))
                .hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
        assertThatThrownBy(() -> fixture.service.getApplicationRelease(
                TENANT,
                "1002",
                "3001",
                fixture.releases.findAll(TENANT, "3001").getFirst().releaseId()
        )).hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
    }

    @Test
    void applicationAclAdministratorCanInspectReleaseHistoryOutsideOwnerSet() {
        Fixture fixture = fixture(
                TENANT,
                publishedDraft("受租户管理员治理的提示词", "9001", NOW.minusSeconds(60)),
                true
        );
        var published = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "managed-release", "交由租户管理员治理"
        ));
        when(fixture.authorization.findPermissionCodes(TENANT, "user", "1002"))
                .thenReturn(Set.of(
                        new PermissionCode("app-studio:application:read"),
                        new PermissionCode("app-studio:application:manage-acl")
                ));
        fixture.service.updateApplication(new UpdateAiApplicationCommand(
                TENANT,
                "1001",
                "3001",
                "未发布治理草稿",
                "不得向治理只读主体泄露的说明",
                "9002",
                List.of("21"),
                "不得向治理只读主体泄露的提示词"
        ));

        assertThat(fixture.service.listApplications(TENANT, "1002"))
                .singleElement()
                .satisfies(catalog -> {
                    assertThat(catalog.accessMode().name()).isEqualTo("GOVERNANCE_READ_ONLY");
                    assertThat(catalog.capabilities().canReadReleaseEvidence()).isTrue();
                    assertThat(catalog.capabilities().canEditConfiguration()).isFalse();
                    assertThat(catalog.capabilities().canRunPublishPreflight()).isFalse();
                    assertThat(catalog.capabilities().canPublish()).isFalse();
                    assertThat(catalog.capabilities().canRollback()).isFalse();
                    assertThat(catalog.capabilities().canManageAccessPolicy()).isFalse();
                    assertThat(catalog.application().name()).isEqualTo("制度助手");
                    assertThat(catalog.application().description()).isNull();
                    assertThat(catalog.application().modelConfigId()).isNull();
                    assertThat(catalog.application().knowledgeBaseIds()).isEmpty();
                    assertThat(catalog.application().systemPrompt()).isNull();
                    assertThat(catalog.application().updatedAt()).isNull();
                });

        assertThat(fixture.service.listApplicationReleases(TENANT, "1002", "3001"))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.releaseId()).isEqualTo(published.activeReleaseId());
                    assertThat(result.modelConfigId()).isEqualTo("9001");
                    assertThat(result.knowledgeBaseIds()).containsExactly("20");
                    assertThat(result.systemPromptSummary()).isNull();
                    assertThat(result.systemPromptSha256()).hasSize(64);
                });
        assertThat(fixture.service.getApplicationRelease(
                TENANT, "1002", "3001", published.activeReleaseId()
        )).satisfies(result -> {
            assertThat(result.releaseId()).isEqualTo(published.activeReleaseId());
            assertThat(result.modelConfigId()).isEqualTo("9001");
            assertThat(result.knowledgeBaseIds()).containsExactly("20");
            assertThat(result.systemPrompt()).isNull();
            assertThat(result.systemPromptSha256()).hasSize(64);
        });
    }

    @Test
    void applicationAclAdministratorCannotMutateApplicationsOutsideOwnerSet() {
        Fixture fixture = fixture(
                TENANT,
                publishedDraft("受租户管理员治理的提示词", "9001", NOW.minusSeconds(60)),
                true
        );
        var published = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "owner-publish", "由应用创建者发布"
        ));

        assertThatThrownBy(() -> fixture.service.updateApplication(new UpdateAiApplicationCommand(
                TENANT,
                "1002",
                "3001",
                "越权更新",
                "租户治理角色不得跨应用更新",
                "9001",
                List.of("20"),
                "越权提示词"
        ))).hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
        assertThatThrownBy(() -> fixture.service.runPublishPreflight(new RunPublishPreflightCommand(
                TENANT,
                "1002",
                "3001",
                "admin-preflight"
        ))).hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
        assertThatThrownBy(() -> fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT,
                "1002",
                "3001",
                "admin-publish",
                "租户治理角色不得跨应用发布"
        ))).hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
        assertThatThrownBy(() -> fixture.service.replaceApplicationAccessPolicy(
                new ReplaceApplicationAccessPolicyCommand(
                        TENANT,
                        "1002",
                        "3001",
                        Set.of("1002"),
                        Set.of(),
                        "租户治理角色不得跨应用改授权"
                )
        )).hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
        assertThatThrownBy(() -> fixture.service.rollbackApplication(new RollbackApplicationReleaseCommand(
                TENANT,
                "1002",
                "3001",
                published.activeReleaseId(),
                "admin-rollback",
                "租户治理角色不得跨应用回滚"
        ))).hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
    }

    @Test
    void rollbackRejectsReleaseOwnedByAnotherTenant() {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版提示词", "9001", NOW.minusSeconds(60)));
        Fixture foreign = fixture(TenantId.of("2"), publishedDraft(
                TenantId.of("2"), "租户二提示词", "9001", NOW.minusSeconds(60)
        ));
        var foreignRelease = foreign.service.publishApplication(new PublishAiApplicationCommand(
                TenantId.of("2"), "1001", "3001", "foreign-release", "租户二上线"
        ));

        assertThatThrownBy(() -> fixture.service.rollbackApplication(new RollbackApplicationReleaseCommand(
                TENANT, "1001", "3001", foreignRelease.activeReleaseId(), "cross-tenant", "非法跨租户回滚"
        )))
                .hasMessageContaining(AppStudioErrorCodes.RESOURCE_NOT_VISIBLE.code());
    }

    @Test
    void rollbackPreflightFailsClosedBeforeCreatingANewRelease() {
        Fixture fixture = fixture(TENANT, publishedDraft("第一版提示词", "9001", NOW.minusSeconds(60)));
        var first = fixture.service.publishApplication(new PublishAiApplicationCommand(
                TENANT, "1001", "3001", "publish-before-block", "首次上线"
        ));
        when(fixture.policies.decide(any())).thenReturn(new AppModelUsageDecision(
                false,
                "model_policy_denied",
                "目标模型已停用",
                null,
                Map.of("policy", "blocked")
        ));

        assertThatThrownBy(() -> fixture.service.rollbackApplication(new RollbackApplicationReleaseCommand(
                TENANT, "1001", "3001", first.activeReleaseId(), "blocked-rollback", "模型停用后禁止回滚"
        )))
                .hasMessageContaining(AppStudioErrorCodes.PUBLISH_BLOCKED.code());
        assertThat(fixture.releases.findAll(TENANT, "3001")).hasSize(1);
    }

    private static Fixture fixture(TenantId tenantId, AiApplication initial) {
        return fixture(tenantId, initial, false);
    }

    private static Fixture fixture(TenantId tenantId, AiApplication initial, boolean canManageApplicationAcl) {
        AtomicReference<AiApplication> application = new AtomicReference<>(initial);
        AiApplicationRepository applications = mock(AiApplicationRepository.class);
        when(applications.findById(any(), any())).thenAnswer(invocation -> {
            TenantId requestedTenant = invocation.getArgument(0);
            String requestedApplication = invocation.getArgument(1);
            AiApplication current = application.get();
            return current.tenantId().equals(requestedTenant) && current.applicationId().equals(requestedApplication)
                    ? Optional.of(current)
                    : Optional.empty();
        });
        when(applications.findCatalogWindowByTenant(any(), any(Integer.class))).thenAnswer(invocation ->
                application.get().tenantId().equals(invocation.getArgument(0))
                        ? List.of(application.get())
                        : List.of());
        when(applications.save(any())).thenAnswer(invocation -> {
            AiApplication saved = invocation.getArgument(0);
            application.set(saved);
            return saved;
        });

        InMemoryReleaseRepository releases = new InMemoryReleaseRepository(application);
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        AtomicReference<ChatSession> savedSession = new AtomicReference<>();
        when(sessions.nextSessionId()).thenReturn("4001");
        when(sessions.nextMessageId()).thenReturn("5001", "5002", "5003", "5004");
        when(sessions.save(any())).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            savedSession.set(session);
            return session;
        });
        when(sessions.findById(any(), any())).thenAnswer(invocation -> {
            ChatSession session = savedSession.get();
            return session != null
                    && session.tenantId().equals(invocation.getArgument(0))
                    && session.sessionId().equals(invocation.getArgument(1))
                    ? Optional.of(session)
                    : Optional.empty();
        });
        when(sessions.appendMessage(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ModelUsagePolicyPort policies = mock(ModelUsagePolicyPort.class);
        when(policies.decide(any())).thenReturn(allowedPolicyDecision());
        PermissionCheckPort permissions = mock(PermissionCheckPort.class);
        when(permissions.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        RbacAuthorizationQueryPort authorization = mock(RbacAuthorizationQueryPort.class);
        Set<PermissionCode> permissionCodes = canManageApplicationAcl
                ? Set.of(
                        new PermissionCode("app-studio:application:read"),
                        new PermissionCode("app-studio:application:update"),
                        new PermissionCode("app-studio:application:publish"),
                        new PermissionCode("app-studio:application:use"),
                        new PermissionCode("app-studio:application:manage-acl"),
                        new PermissionCode("app-studio:model:use")
                )
                : Set.of(
                        new PermissionCode("app-studio:application:read"),
                        new PermissionCode("app-studio:application:update"),
                        new PermissionCode("app-studio:application:publish"),
                        new PermissionCode("app-studio:application:use"),
                        new PermissionCode("app-studio:model:use")
                );
        when(authorization.findPermissionCodes(any(), any(), any())).thenReturn(permissionCodes);
        ResourceAudienceQueryPort audience = mock(ResourceAudienceQueryPort.class);
        when(audience.findAllowedUserIds(any(), any(), any(), any())).thenReturn(Set.of("1002"));

        KnowledgeRetrievalPort retrieval = mock(KnowledgeRetrievalPort.class);
        KnowledgeRetrievalCitation retrievalCitation = new KnowledgeRetrievalCitation(
                "20", "18", "1", "企业手册", "命中片段", "p1", 0.88
        );
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence(
                "20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1"
        );
        when(retrieval.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(
                true,
                List.of(retrievalCitation),
                List.of(evidence),
                null
        ));
        ChatCompletionPort completion = mock(ChatCompletionPort.class);
        when(completion.complete(any())).thenReturn(new ChatCompletionResult(
                "基于第一版发布快照回答",
                List.of(new Citation("20", "18", "1", "企业手册", "命中片段", "p1", 0.88)),
                "8801",
                Map.of("provider", "test")
        ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.createWithReleases(
                applications,
                sessions,
                retrieval,
                policies,
                completion,
                mock(AppStudioDebugRunSnapshotPort.class),
                mock(AppStudioDebugRunQueryPort.class),
                permissions,
                authorization,
                audience,
                releases,
                mock(AuditEventPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, releases, policies, retrieval, completion, authorization);
    }

    private static AiApplication publishedDraft(String prompt, String modelId, Instant updatedAt) {
        return publishedDraft(TENANT, prompt, modelId, updatedAt);
    }

    private static AiApplication publishedDraft(TenantId tenantId, String prompt, String modelId, Instant updatedAt) {
        return new AiApplication(
                tenantId,
                "3001",
                AiApplicationType.KNOWLEDGE_QA,
                "制度助手",
                "企业制度问答",
                modelId,
                List.of("20"),
                List.of(),
                Set.of("1001"),
                prompt,
                ApplicationPublicationStatus.PUBLISHED,
                NOW.minusSeconds(3600),
                updatedAt
        );
    }

    private static AppModelUsageDecision allowedPolicyDecision() {
        return new AppModelUsageDecision(
                true,
                "ALLOW",
                "命中已发布策略",
                new AppModelRuntimeDescriptor(
                        "6",
                        "3001",
                        "test-provider",
                        "openai-compatible",
                        new AppModelRuntimeConnectionRef("https://provider.example/v1", "api_key", "secret-test"),
                        "test-chat-model",
                        7,
                        "CHAT",
                        Set.of("chat-completion"),
                        Map.of(),
                        "a".repeat(64),
                        "v1"
                ),
                Map.of("policy", "test")
        );
    }

    private record Fixture(
            AppStudioApplicationService service,
            InMemoryReleaseRepository releases,
            ModelUsagePolicyPort policies,
            KnowledgeRetrievalPort retrieval,
            ChatCompletionPort completion,
            RbacAuthorizationQueryPort authorization
    ) {
    }

    private static final class InMemoryReleaseRepository implements ApplicationReleaseRepository {
        private final AtomicReference<AiApplication> application;
        private final Map<String, AiApplicationRelease> byRequest = new LinkedHashMap<>();
        private final List<AiApplicationRelease> releases = new ArrayList<>();
        private AiApplicationRelease active;

        private InMemoryReleaseRepository(AtomicReference<AiApplication> application) {
            this.application = application;
        }

        @Override
        public synchronized Optional<AiApplicationRelease> findActive(TenantId tenantId, String applicationId) {
            return Optional.ofNullable(active)
                    .filter(release -> release.tenantId().equals(tenantId)
                            && release.applicationId().equals(applicationId));
        }

        @Override
        public synchronized Map<String, AiApplicationRelease> findActiveByApplicationIds(
                TenantId tenantId,
                List<String> applicationIds
        ) {
            if (active == null || !active.tenantId().equals(tenantId)
                    || !applicationIds.contains(active.applicationId())) {
                return Map.of();
            }
            return Map.of(active.applicationId(), active);
        }

        @Override
        public synchronized Optional<AiApplicationRelease> findByRequestId(
                TenantId tenantId,
                String applicationId,
                String requestId
        ) {
            return Optional.ofNullable(byRequest.get(key(tenantId, applicationId, requestId)));
        }

        @Override
        public synchronized Optional<AiApplicationRelease> findById(
                TenantId tenantId,
                String applicationId,
                String releaseId
        ) {
            return releases.stream()
                    .filter(release -> release.tenantId().equals(tenantId))
                    .filter(release -> release.applicationId().equals(applicationId))
                    .filter(release -> release.releaseId().equals(releaseId))
                    .findFirst();
        }

        @Override
        public synchronized List<AiApplicationRelease> findAll(TenantId tenantId, String applicationId) {
            return releases.stream()
                    .filter(release -> release.tenantId().equals(tenantId))
                    .filter(release -> release.applicationId().equals(applicationId))
                    .sorted((left, right) -> Long.compare(right.version(), left.version()))
                    .toList();
        }

        @Override
        public synchronized MutationResult createAndActivate(MutationCommand command) {
            String requestKey = key(command.tenantId(), command.applicationId(), command.requestId());
            AiApplicationRelease existing = byRequest.get(requestKey);
            if (existing != null) {
                return new MutationResult(existing, false);
            }
            long version = releases.stream()
                    .filter(release -> release.tenantId().equals(command.tenantId()))
                    .filter(release -> release.applicationId().equals(command.applicationId()))
                    .mapToLong(AiApplicationRelease::version)
                    .max()
                    .orElse(0) + 1;
            AiApplication snapshot = new AiApplication(
                    command.snapshot().tenantId(),
                    command.snapshot().applicationId(),
                    command.snapshot().type(),
                    command.snapshot().name(),
                    command.snapshot().description(),
                    command.snapshot().modelConfigId(),
                    command.snapshot().knowledgeBaseIds(),
                    command.snapshot().toolIds(),
                    command.snapshot().visiblePrincipalIds(),
                    command.snapshot().systemPrompt(),
                    ApplicationPublicationStatus.PUBLISHED,
                    command.snapshot().createdAt(),
                    command.occurredAt()
            );
            AiApplicationRelease release = new AiApplicationRelease(
                    String.valueOf(7000 + version),
                    command.tenantId(),
                    command.applicationId(),
                    version,
                    command.sourceReleaseId(),
                    command.requestId(),
                    command.operation(),
                    snapshot,
                    command.allowedUserIds(),
                    command.actorId(),
                    command.reason(),
                    command.traceId(),
                    command.occurredAt()
            );
            releases.add(release);
            byRequest.put(requestKey, release);
            active = release;
            AiApplication draft = application.get();
            application.set(new AiApplication(
                    draft.tenantId(),
                    draft.applicationId(),
                    draft.type(),
                    draft.name(),
                    draft.description(),
                    draft.modelConfigId(),
                    draft.knowledgeBaseIds(),
                    draft.toolIds(),
                    draft.visiblePrincipalIds(),
                    draft.systemPrompt(),
                    ApplicationPublicationStatus.PUBLISHED,
                    draft.createdAt(),
                    command.occurredAt()
            ));
            return new MutationResult(release, true);
        }

        private static String key(TenantId tenantId, String applicationId, String requestId) {
            return tenantId.value() + ':' + applicationId + ':' + requestId;
        }
    }
}
