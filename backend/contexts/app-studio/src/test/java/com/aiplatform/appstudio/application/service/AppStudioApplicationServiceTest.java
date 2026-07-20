package com.aiplatform.appstudio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.application.command.CreateAiApplicationCommand;
import com.aiplatform.appstudio.application.command.CreateChatSessionCommand;
import com.aiplatform.appstudio.application.command.PublishAiApplicationCommand;
import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.command.UpdateAiApplicationCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.application.port.ChatRunRepository;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionRequest;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionResult;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunSnapshotPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCitation;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.AuthorizationRequestTarget;
import com.aiplatform.shared.kernel.RequestableAuthorizationException;
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

class AppStudioApplicationServiceTest {
    private static final TenantId TENANT_ID = TenantId.of("2001");
    private static final Instant NOW = Instant.parse("2026-06-10T12:00:00Z");

    @Test
    void listApplicationsReturnsVisibleApplications() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findCatalogWindowByTenant(TENANT_ID, 201))
                .thenReturn(List.of(application(), draftApplication()));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                denyAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.listApplications(TENANT_ID, "1001"))
                .extracting(result -> result.application().applicationId())
                .containsExactly("3001", "3001");
        assertThat(service.listApplications(TENANT_ID, "1001"))
                .allSatisfy(result -> {
                    assertThat(result.accessMode().name()).isEqualTo("OWNER");
                    assertThat(result.capabilities().canEditConfiguration()).isTrue();
                    assertThat(result.capabilities().canRunPublishPreflight()).isTrue();
                    assertThat(result.capabilities().canPublish()).isTrue();
                    assertThat(result.capabilities().canRollback()).isTrue();
                    assertThat(result.capabilities().canManageAccessPolicy()).isFalse();
                    assertThat(result.capabilities().canReadReleaseEvidence()).isTrue();
                });
    }

    @Test
    void resourceAclMakesPublishedApplicationVisibleAndUsableForBusinessUser() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        AiApplication authorized = new AiApplication(
                TENANT_ID, "3002", AiApplicationType.KNOWLEDGE_QA, "客服助手", "企业客服", "9001",
                List.of("20"), List.of(), Set.of("1002"), "请基于知识库回答",
                ApplicationPublicationStatus.PUBLISHED, NOW, NOW
        );
        when(applicationRepository.findCatalogWindowByTenant(TENANT_ID, 201)).thenReturn(List.of(authorized));
        when(applicationRepository.findById(TENANT_ID, "3002")).thenReturn(Optional.of(authorized));
        when(sessionRepository.nextSessionId()).thenReturn("4002");
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RbacAuthorizationQueryPort authorizationQueryPort = Mockito.mock(RbacAuthorizationQueryPort.class);
        when(authorizationQueryPort.findPermissionCodes(TENANT_ID, "user", "1003"))
                .thenReturn(Set.of(
                        new PermissionCode("app-studio:application:read"),
                        new PermissionCode("app-studio:application:use"),
                        new PermissionCode("app-studio:model:use")
                ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                authorizationQueryPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.listApplications(TENANT_ID, "1003"))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.application().applicationId()).isEqualTo("3002");
                    assertThat(result.accessMode().name()).isEqualTo("BUSINESS_USER");
                    assertThat(result.capabilities().canReadReleaseEvidence()).isFalse();
                    assertThat(result.capabilities().canEditConfiguration()).isFalse();
                    assertThat(result.application().modelConfigId()).isNull();
                    assertThat(result.application().knowledgeBaseIds()).isEmpty();
                    assertThat(result.application().systemPrompt()).isNull();
                });
        assertThat(service.createSession(new CreateChatSessionCommand(
                TENANT_ID, "1003", "3002", "客服咨询", null
        )).userId()).isEqualTo("1003");
    }

    @Test
    void applicationAclAdministratorCanListApplicationsOutsideActiveAudience() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        RbacAuthorizationQueryPort authorizationQueryPort = Mockito.mock(RbacAuthorizationQueryPort.class);
        AiApplication governedApplication = application();
        when(applicationRepository.findCatalogWindowByTenant(TENANT_ID, 201)).thenReturn(List.of(governedApplication));
        when(applicationRepository.findById(TENANT_ID, governedApplication.applicationId()))
                .thenReturn(Optional.of(governedApplication));
        when(authorizationQueryPort.findPermissionCodes(TENANT_ID, "user", "1002"))
                .thenReturn(Set.of(
                        new PermissionCode("app-studio:application:read"),
                        new PermissionCode("app-studio:application:use"),
                        new PermissionCode("app-studio:application:manage-acl")
                ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                denyAllPermissionCheck(),
                authorizationQueryPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.listApplications(TENANT_ID, "1002"))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.accessMode().name()).isEqualTo("GOVERNANCE_READ_ONLY");
                    assertThat(result.capabilities().canReadReleaseEvidence()).isTrue();
                    assertThat(result.capabilities().canEditConfiguration()).isFalse();
                    assertThat(result.capabilities().canRunPublishPreflight()).isFalse();
                    assertThat(result.capabilities().canPublish()).isFalse();
                    assertThat(result.capabilities().canRollback()).isFalse();
                    assertThat(result.capabilities().canManageAccessPolicy()).isFalse();
                    assertThat(result.application().applicationId()).isEqualTo(governedApplication.applicationId());
                    assertThat(result.application().description()).isNull();
                    assertThat(result.application().modelConfigId()).isNull();
                    assertThat(result.application().knowledgeBaseIds()).isEmpty();
                    assertThat(result.application().systemPrompt()).isNull();
                    assertThat(result.application().updatedAt()).isNull();
                });
    }

    @Test
    void applicationAclAdministratorDiscoversUnpublishedApplicationWithoutDraftContent() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        AiApplication unpublished = draftApplication();
        when(applicationRepository.findCatalogWindowByTenant(TENANT_ID, 201)).thenReturn(List.of(unpublished));
        RbacAuthorizationQueryPort authorizationQueryPort = Mockito.mock(RbacAuthorizationQueryPort.class);
        when(authorizationQueryPort.findPermissionCodes(TENANT_ID, "user", "1002"))
                .thenReturn(Set.of(
                        new PermissionCode("app-studio:application:read"),
                        new PermissionCode("app-studio:application:manage-acl")
                ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                Mockito.mock(ChatSessionRepository.class),
                Mockito.mock(KnowledgeRetrievalPort.class),
                Mockito.mock(ModelUsagePolicyPort.class),
                Mockito.mock(ChatCompletionPort.class),
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                denyAllPermissionCheck(),
                authorizationQueryPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.listApplications(TENANT_ID, "1002"))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.accessMode().name()).isEqualTo("GOVERNANCE_READ_ONLY");
                    assertThat(result.application().name()).isEqualTo("未发布应用 APP-3001");
                    assertThat(result.application().name()).doesNotContain(unpublished.name());
                    assertThat(result.application().description()).isNull();
                    assertThat(result.application().applicationType()).isNull();
                    assertThat(result.application().modelConfigId()).isNull();
                    assertThat(result.application().knowledgeBaseIds()).isEmpty();
                    assertThat(result.application().systemPrompt()).isNull();
                    assertThat(result.application().publicationStatus()).isEqualTo("UNPUBLISHED");
                    assertThat(result.application().updatedAt()).isNull();
                });
    }

    @Test
    void createApplicationNormalizesFactsAndPersistsBoundKnowledgeBases() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.nextApplicationId()).thenReturn("3001");
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.createApplication(new CreateAiApplicationCommand(
                TENANT_ID,
                "1001",
                AiApplicationType.KNOWLEDGE_QA,
                "客服助手",
                "企业客服",
                "9001",
                List.of("20", "20"),
                "请基于知识库回答"
        ));

        ArgumentCaptor<AiApplication> captor = ArgumentCaptor.forClass(AiApplication.class);
        Mockito.verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().modelConfigId()).isEqualTo("9001");
        assertThat(captor.getValue().knowledgeBaseIds()).containsExactly("20");
        assertThat(result.modelConfigId()).isEqualTo("9001");
        assertThat(result.accessMode().name()).isEqualTo("OWNER");
        assertThat(result.capabilities().canEditConfiguration()).isTrue();
        assertThat(result.capabilities().canPublish()).isTrue();
        assertThat(result.capabilities().canManageAccessPolicy()).isFalse();
    }

    @Test
    void createApplicationRejectsActorWithoutCreatePermission() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        PermissionCheckPort permissionCheckPort = Mockito.mock(PermissionCheckPort.class);
        RbacAuthorizationQueryPort authorizationQueryPort = Mockito.mock(RbacAuthorizationQueryPort.class);
        when(applicationRepository.nextApplicationId()).thenReturn("3001");
        when(permissionCheckPort.canAccess(any(), any(), any(), any(), any())).thenReturn(false);
        when(authorizationQueryPort.findPermissionCodes(any(), any(), any())).thenReturn(Set.of());

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                permissionCheckPort,
                authorizationQueryPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.createApplication(new CreateAiApplicationCommand(
                TENANT_ID,
                "1001",
                AiApplicationType.KNOWLEDGE_QA,
                "客服助手",
                "企业客服",
                "9001",
                List.of("20"),
                "请基于知识库回答"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.AUTHORIZATION_DENIED.code());
    }

    @Test
    void updateApplicationCommandNormalizesFactsBeforeUse() {
        UpdateAiApplicationCommand command = new UpdateAiApplicationCommand(
                TENANT_ID,
                " 1001 ",
                " 3001 ",
                " 智能客服 ",
                "  面向企业客服  ",
                " 9001 ",
                List.of(" 20 ", "20", " 21 "),
                " 请基于知识库回答 "
        );

        assertThat(command.actorId()).isEqualTo("1001");
        assertThat(command.applicationId()).isEqualTo("3001");
        assertThat(command.name()).isEqualTo("智能客服");
        assertThat(command.description()).isEqualTo("面向企业客服");
        assertThat(command.modelConfigId()).isEqualTo("9001");
        assertThat(command.knowledgeBaseIds()).containsExactly("20", "21");
        assertThat(command.systemPrompt()).isEqualTo("请基于知识库回答");
    }

    @Test
    void publishApplicationCommandNormalizesFactsBeforeUse() {
        PublishAiApplicationCommand command = new PublishAiApplicationCommand(TENANT_ID, " 1001 ", " 3001 ");

        assertThat(command.actorId()).isEqualTo("1001");
        assertThat(command.applicationId()).isEqualTo("3001");
    }

    @Test
    void sendMessageReturnsAnswerWithCitationsAndEvidences() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        Citation citation = new Citation("20", "18", "1", "企业手册", "命中片段", "p1", 0.88);
        KnowledgeRetrievalCitation retrievalCitation = new KnowledgeRetrievalCitation("20", "18", "1", "企业手册", "命中片段", "p1", 0.88);
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence("20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1");
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(true, List.of(retrievalCitation), List.of(evidence), null));
        when(policyPort.decide(any())).thenReturn(new AppModelUsageDecision(
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
        ));
        when(completionPort.complete(any())).thenReturn(new ChatCompletionResult("基于知识库的回答", List.of(citation), "88", Map.of("provider", "sf")));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.sendMessage(new SendChatMessageCommand(TENANT_ID, "1001", "4001", "差旅报销规则是什么？", "trace-1"));

        ArgumentCaptor<AppModelUsageRequest> policyCaptor = ArgumentCaptor.forClass(AppModelUsageRequest.class);
        Mockito.verify(policyPort).decide(policyCaptor.capture());
        assertThat(policyCaptor.getValue().useCase()).isEqualTo("RAG_QUERY");
        assertThat(policyCaptor.getValue().requiredCapability()).isEqualTo("CHAT");
        ArgumentCaptor<ChatCompletionRequest> completionCaptor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        Mockito.verify(completionPort).complete(completionCaptor.capture());
        assertThat(completionCaptor.getValue().traceId()).isEqualTo("trace-1");
        assertThat(completionCaptor.getValue().idempotencyKey()).isEqualTo("trace-1");
        assertThat(completionCaptor.getValue().applicationInstruction()).isEqualTo("请基于知识库回答");
        assertThat(completionCaptor.getValue().userQuestion()).isEqualTo("差旅报销规则是什么？");
        assertThat(completionCaptor.getValue().citations()).containsExactly(citation);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        Mockito.verify(sessionRepository, Mockito.times(2)).appendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues().get(1).modelInvocationLogId()).isEqualTo("88");
        assertThat(result.answerable()).isTrue();
        assertThat(result.answer()).isEqualTo("基于知识库的回答");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.evidences()).hasSize(1);
        assertThat(result.noAnswerReason()).isNull();
    }

    @Test
    void sendMessageKeepsAuthorizedRetrievalCitationsWhenAdapterReturnsForeignCitation() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Citation authorized = new Citation("20", "18", "1", "企业手册", "授权片段", "p1", 0.88);
        KnowledgeRetrievalCitation retrievalCitation = new KnowledgeRetrievalCitation(
                "20", "18", "1", "企业手册", "授权片段", "p1", 0.88
        );
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence(
                "20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1"
        );
        Citation foreign = new Citation(
                "other-tenant-kb", "foreign-doc", "foreign-chunk", "越权文档", "越权片段", "p9", 0.99
        );
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(
                true, List.of(retrievalCitation), List.of(evidence), null
        ));
        when(policyPort.decide(any())).thenReturn(allowedPolicyDecision());
        when(completionPort.complete(any())).thenReturn(new ChatCompletionResult(
                "仅使用授权证据回答", List.of(foreign), "88", Map.of()
        ));
        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.sendMessage(new SendChatMessageCommand(
                TENANT_ID, "1001", "4001", "差旅报销规则是什么？", "trace-authorized-citation"
        ));

        assertThat(result.citations()).extracting("knowledgeBaseId", "documentId", "chunkId")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("20", "18", "1"));
        ArgumentCaptor<ChatMessage> messages = ArgumentCaptor.forClass(ChatMessage.class);
        Mockito.verify(sessionRepository, Mockito.times(2)).appendMessage(messages.capture());
        assertThat(messages.getAllValues().get(1).citations()).containsExactly(authorized);
    }

    @Test
    void invalidAnswerableRetrievalDoesNotRecordModelDispatchOrCallProvider() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        ChatRunTransactionService chatRuns = Mockito.mock(ChatRunTransactionService.class);
        KnowledgeRetrievalResponse invalidRetrieval = Mockito.mock(KnowledgeRetrievalResponse.class);
        ChatRunRepository.Ownership ownership = new ChatRunRepository.Ownership(
                TENANT_ID, "run-1", "4001", "3001", "1001", "trace-empty-citation", "lease-1", 1
        );
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001");
        when(retrievalPort.retrieve(any())).thenReturn(invalidRetrieval);
        when(invalidRetrieval.answerable()).thenReturn(true);
        when(invalidRetrieval.citations()).thenReturn(List.of());
        when(invalidRetrieval.evidences()).thenReturn(List.of());
        when(policyPort.decide(any())).thenReturn(allowedPolicyDecision());
        when(chatRuns.claim(any(), Mockito.eq("3001"))).thenReturn(ChatRunRepository.Claim.acquired(ownership));
        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.createWithChatRuns(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                chatRuns,
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.sendMessage(new SendChatMessageCommand(
                TENANT_ID, "1001", "4001", "没有授权引用的问题", "trace-empty-citation"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("检索引用不能为空，证据不足时禁止调用模型");
        Mockito.verify(chatRuns, Mockito.never()).markModelDispatched(any());
        Mockito.verify(completionPort, Mockito.never()).complete(any());
    }

    @Test
    void sendMessageFallsBackToCitationFragmentsWhenModelReturnsUngroundedNoAnswer() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        Citation citation = new Citation("20", "18", "1", "企业手册", "制度内容", "p1", 0.88);
        KnowledgeRetrievalCitation retrievalCitation = new KnowledgeRetrievalCitation("20", "18", "1", "企业手册", "制度内容", "p1", 0.88);
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence("20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1");
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(true, List.of(retrievalCitation), List.of(evidence), null));
        when(policyPort.decide(any())).thenReturn(new AppModelUsageDecision(
                true,
                "ALLOW",
                "命中已发布策略",
                new AppModelRuntimeDescriptor(
                        "5",
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
        ));
        when(completionPort.complete(any())).thenReturn(new ChatCompletionResult(
                "未找到具体的制度内容原文。",
                List.of(citation),
                Map.of("provider", "sf")
        ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.sendMessage(new SendChatMessageCommand(TENANT_ID, "1001", "4001", "制度内容是什么？", "trace-1"));

        assertThat(result.answerable()).isTrue();
        assertThat(result.answer()).isEqualTo("根据引用原文，制度内容如下：\n1. 制度内容");
        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void sendMessageFallsBackToCitationFragmentsWhenModelReturnsUnsupportedNumber() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        String fragment = "国内交通补贴标准为每天 200 元，票据需在 5 个工作日内提交。";
        Citation citation = new Citation("20", "18", "1", "企业手册", fragment, "p1", 0.88);
        KnowledgeRetrievalCitation retrievalCitation = new KnowledgeRetrievalCitation("20", "18", "1", "企业手册", fragment, "p1", 0.88);
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence("20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1");
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(true, List.of(retrievalCitation), List.of(evidence), null));
        when(policyPort.decide(any())).thenReturn(new AppModelUsageDecision(
                true,
                "ALLOW",
                "命中已发布策略",
                new AppModelRuntimeDescriptor(
                        "5",
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
        ));
        when(completionPort.complete(any())).thenReturn(new ChatCompletionResult(
                "国内交通补贴标准为每天 80 元，票据需在 5 个工作日内提交。",
                List.of(citation),
                Map.of("provider", "sf")
        ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.sendMessage(new SendChatMessageCommand(TENANT_ID, "1001", "4001", "补贴标准是多少？", "trace-1"));

        assertThat(result.answerable()).isTrue();
        assertThat(result.answer()).isEqualTo("根据引用原文，制度内容如下：\n1. " + fragment);
        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void sendMessagePersistsAssistantFailureRunWhenModelInvocationFails() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        AppStudioDebugRunSnapshotPort snapshotPort = Mockito.mock(AppStudioDebugRunSnapshotPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        KnowledgeRetrievalCitation retrievalCitation = new KnowledgeRetrievalCitation("20", "18", "1", "企业手册", "命中片段", "p1", 0.88);
        KnowledgeRetrievalEvidence evidence = new KnowledgeRetrievalEvidence("20", "18", "1", 0.88, "VECTOR", "kb-20-active-v1");
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(true, List.of(retrievalCitation), List.of(evidence), null));
        when(policyPort.decide(any())).thenReturn(new AppModelUsageDecision(
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
        ));
        when(completionPort.complete(any())).thenReturn(ChatCompletionResult.failed(
                "99",
                "IllegalStateException",
                "simulated model outage with apiKey=secret"
        ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                snapshotPort,
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.sendMessage(new SendChatMessageCommand(TENANT_ID, "1001", "4001", "差旅报销规则是什么？", "trace-failed-1"));

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        Mockito.verify(sessionRepository, Mockito.times(2)).appendMessage(messageCaptor.capture());
        ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
        assertThat(assistantMessage.modelInvocationLogId()).isEqualTo("99");
        assertThat(assistantMessage.content()).isEqualTo("模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。");
        assertThat(assistantMessage.citations()).isEmpty();
        assertThat(result.answerable()).isFalse();
        assertThat(result.noAnswerReason()).isEqualTo("模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。");
        assertThat(result.metadata())
                .containsEntry("invocationStatus", "FAILED")
                .containsEntry("decisionSource", "model-invocation-failed")
                .containsEntry("errorCode", "IllegalStateException");
        ArgumentCaptor<com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot> snapshotCaptor = ArgumentCaptor.forClass(com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot.class);
        Mockito.verify(snapshotPort).record(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().assistantMessageId()).isEqualTo("5002");
        assertThat(snapshotCaptor.getValue().evidences()).hasSize(1);
    }

    @Test
    void sendMessageFailsClosedWhenRetrievalCannotAnswer() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.nextMessageId()).thenReturn("5001", "5002");
        when(sessionRepository.appendMessage(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(retrievalPort.retrieve(any())).thenReturn(new KnowledgeRetrievalResponse(false, List.of(), List.of(), "知识库未召回可用证据"));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.sendMessage(new SendChatMessageCommand(TENANT_ID, "1001", "4001", "差旅报销规则是什么？", "trace-1"));

        assertThat(result.answerable()).isFalse();
        assertThat(result.noAnswerReason()).isEqualTo("知识库未召回可用证据");
        Mockito.verifyNoInteractions(policyPort, completionPort);
    }

    @Test
    void updateApplicationRewritesConfigAndKnowledgeBaseBindings() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.updateApplication(new UpdateAiApplicationCommand(
                TENANT_ID,
                "1001",
                "3001",
                "制度问答助手",
                "更新后的应用描述",
                "9002",
                List.of("21", "22", "21"),
                "请优先给出引用原文。"
        ));

        ArgumentCaptor<AiApplication> captor = ArgumentCaptor.forClass(AiApplication.class);
        Mockito.verify(applicationRepository).save(captor.capture());
        org.mockito.InOrder mutationOrder = Mockito.inOrder(applicationRepository);
        mutationOrder.verify(applicationRepository).acquireMutationLock(TENANT_ID, "3001");
        mutationOrder.verify(applicationRepository).findById(TENANT_ID, "3001");
        mutationOrder.verify(applicationRepository).save(any());
        assertThat(captor.getValue().applicationId()).isEqualTo("3001");
        assertThat(captor.getValue().name()).isEqualTo("制度问答助手");
        assertThat(captor.getValue().description()).isEqualTo("更新后的应用描述");
        assertThat(captor.getValue().modelConfigId()).isEqualTo("9002");
        assertThat(captor.getValue().knowledgeBaseIds()).containsExactly("21", "22");
        assertThat(captor.getValue().systemPrompt()).isEqualTo("请优先给出引用原文。");
        assertThat(captor.getValue().publicationStatus()).isEqualTo(ApplicationPublicationStatus.PUBLISHED);
        assertThat(result.modelConfigId()).isEqualTo("9002");
        assertThat(result.knowledgeBaseIds()).containsExactly("21", "22");
    }

    @Test
    void publishApplicationTransitionsDraftToPublished() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(draftApplication()));
        when(applicationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(policyPort.decide(any())).thenReturn(allowedPolicyDecision());

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.publishApplication(new PublishAiApplicationCommand(TENANT_ID, "1001", "3001"));

        ArgumentCaptor<AiApplication> captor = ArgumentCaptor.forClass(AiApplication.class);
        Mockito.verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().publicationStatus()).isEqualTo(ApplicationPublicationStatus.PUBLISHED);
        assertThat(result.publicationStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void publishApplicationFailsClosedWhenModelPolicyProjectionIsMissing() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(draftApplication()));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                Mockito.mock(KnowledgeRetrievalPort.class),
                policyPort,
                Mockito.mock(ChatCompletionPort.class),
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.publishApplication(
                new PublishAiApplicationCommand(TENANT_ID, "1001", "3001")
        ))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.PUBLISH_BLOCKED.code());
        Mockito.verify(applicationRepository, Mockito.never()).save(any());
    }

    @Test
    void createSessionPersistsRequestedTitle() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.nextSessionId()).thenReturn("4002");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var result = service.createSession(new CreateChatSessionCommand(TENANT_ID, "1001", "3001", "制度答疑会话", "trace-1"));

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        Mockito.verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("制度答疑会话");
        assertThat(result.title()).isEqualTo("制度答疑会话");
    }

    @Test
    void createSessionReturnsTheExactRequestableTargetForAclOnlyDenial() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        AiApplication hidden = new AiApplication(
                TENANT_ID, "3001", AiApplicationType.KNOWLEDGE_QA, "客服助手", "企业客服", "9001",
                List.of("20"), List.of(), Set.of("9999"), "请基于知识库回答", ApplicationPublicationStatus.PUBLISHED, NOW, NOW);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(hidden));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                denyAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.createSession(new CreateChatSessionCommand(TENANT_ID, "1001", "3001", null, null)))
                .isInstanceOfSatisfying(RequestableAuthorizationException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(AppStudioErrorCodes.RESOURCE_ACL_REQUESTABLE);
                    assertThat(exception.target()).isEqualTo(new AuthorizationRequestTarget(
                            "AI_APPLICATION",
                            "3001",
                            "app-studio:application:use"
                    ));
                });
    }


    @Test
    void createSessionRejectsApplicationWithoutUsePermission() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        PermissionCheckPort permissionCheckPort = Mockito.mock(PermissionCheckPort.class);
        RbacAuthorizationQueryPort authorizationQueryPort = Mockito.mock(RbacAuthorizationQueryPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(permissionCheckPort.canAccess(any(), any(), any(), any(), any())).thenReturn(false);
        when(authorizationQueryPort.findPermissionCodes(any(), any(), any()))
                .thenReturn(Set.of(new PermissionCode("app-studio:application:read")));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                permissionCheckPort,
                authorizationQueryPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.createSession(new CreateChatSessionCommand(TENANT_ID, "1001", "3001", null, null)))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(AppStudioErrorCodes.AUTHORIZATION_DENIED.code());
    }

    @Test
    void listSessionsReturnsOnlyCurrentUserSessionsForApplication() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(applicationRepository.findById(TENANT_ID, "3001")).thenReturn(Optional.of(application()));
        when(sessionRepository.listSessions(TENANT_ID, "3001", "1001")).thenReturn(List.of(
                new ChatSession(TENANT_ID, "4001", "3001", "test-release-3001", 1, "1001", "制度问答", NOW, NOW),
                new ChatSession(TENANT_ID, "4002", "3001", "test-release-3001", 1, "1001", "发布验收", NOW, NOW)
        ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.listSessions(TENANT_ID, "1001", "3001"))
                .extracting(result -> result.title())
                .containsExactly("制度问答", "发布验收");
    }

    @Test
    void listMessagesReturnsPersistedHistoryForOwnedSession() {
        AiApplicationRepository applicationRepository = Mockito.mock(AiApplicationRepository.class);
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        KnowledgeRetrievalPort retrievalPort = Mockito.mock(KnowledgeRetrievalPort.class);
        ModelUsagePolicyPort policyPort = Mockito.mock(ModelUsagePolicyPort.class);
        ChatCompletionPort completionPort = Mockito.mock(ChatCompletionPort.class);
        when(sessionRepository.findById(TENANT_ID, "4001")).thenReturn(Optional.of(session()));
        when(sessionRepository.listMessages(TENANT_ID, "4001")).thenReturn(List.of(
                new ChatMessage(TENANT_ID, "5001", "4001", ChatRole.USER, "制度内容是什么？", List.of(), NOW),
                new ChatMessage(TENANT_ID, "5002", "4001", ChatRole.ASSISTANT, "制度内容如下。", List.of(
                        new Citation("21", "18", "19", "员工手册", "制度内容", "p1", 0.91)
                ), "model-log-5002", "trace-5002", NOW)
        ));

        AppStudioApplicationService service = AppStudioApplicationServiceTestFactory.create(
                applicationRepository,
                sessionRepository,
                retrievalPort,
                policyPort,
                completionPort,
                noopDebugRunSnapshotPort(),
                noopDebugRunQueryPort(),
                allowAllPermissionCheck(),
                allowAllAuthorizationQuery(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var history = service.listMessages(TENANT_ID, "1001", "4001");

        assertThat(history)
                .extracting(result -> result.content() + "#" + result.citationCount())
                .containsExactly("制度内容是什么？#0", "制度内容如下。#1");
        assertThat(history.get(1).applicationId()).isEqualTo("3001");
        assertThat(history.get(1).messageId()).isEqualTo("5002");
        assertThat(history.get(1).modelInvocationLogId()).isEqualTo("model-log-5002");
        assertThat(history.get(1).traceId()).isEqualTo("trace-5002");
        assertThat(history.get(1).citations()).singleElement().satisfies(citation -> {
            assertThat(citation.knowledgeBaseId()).isEqualTo("21");
            assertThat(citation.documentId()).isEqualTo("18");
            assertThat(citation.chunkId()).isEqualTo("19");
            assertThat(citation.title()).isEqualTo("员工手册");
            assertThat(citation.snippet()).isEqualTo("制度内容");
            assertThat(citation.locator()).isEqualTo("p1");
            assertThat(citation.score()).isEqualTo(0.91);
        });
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

    private static AiApplication draftApplication() {
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
                ApplicationPublicationStatus.DRAFT,
                NOW,
                NOW
        );
    }

    private static AppStudioDebugRunSnapshotPort noopDebugRunSnapshotPort() {
        return Mockito.mock(AppStudioDebugRunSnapshotPort.class);
    }

    private static AppStudioDebugRunQueryPort noopDebugRunQueryPort() {
        return Mockito.mock(AppStudioDebugRunQueryPort.class);
    }

    private static PermissionCheckPort allowAllPermissionCheck() {
        PermissionCheckPort permissionCheckPort = Mockito.mock(PermissionCheckPort.class);
        when(permissionCheckPort.canAccess(any(), any(), any(), any(), any())).thenReturn(true);
        return permissionCheckPort;
    }

    private static PermissionCheckPort denyAllPermissionCheck() {
        return Mockito.mock(PermissionCheckPort.class);
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
                        new com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef(
                                "https://provider.example/v1",
                                "api_key",
                                "secret-test"
                        ),
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
}
