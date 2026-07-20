package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.command.CreateAiApplicationCommand;
import com.aiplatform.appstudio.application.command.CreateChatSessionCommand;
import com.aiplatform.appstudio.application.command.GetDebugEvidenceCommand;
import com.aiplatform.appstudio.application.command.PublishAiApplicationCommand;
import com.aiplatform.appstudio.application.command.ReplaceApplicationAccessPolicyCommand;
import com.aiplatform.appstudio.application.command.RollbackApplicationReleaseCommand;
import com.aiplatform.appstudio.application.command.RunPublishPreflightCommand;
import com.aiplatform.appstudio.application.command.SendChatMessageCommand;
import com.aiplatform.appstudio.application.command.UpdateAiApplicationCommand;
import com.aiplatform.appstudio.application.error.AppStudioErrorCodes;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Claim;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ClaimState;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Finalization;
import com.aiplatform.appstudio.application.port.ChatRunRepository.Ownership;
import com.aiplatform.appstudio.application.result.AiApplicationCatalogResult;
import com.aiplatform.appstudio.application.result.AiApplicationDetailResult;
import com.aiplatform.appstudio.application.result.ApplicationAccessMode;
import com.aiplatform.appstudio.application.result.ApplicationAccessPolicyResult;
import com.aiplatform.appstudio.application.result.ApplicationCapabilitiesResult;
import com.aiplatform.appstudio.application.result.ApplicationReleaseDetailResult;
import com.aiplatform.appstudio.application.result.ApplicationReleaseSummaryResult;
import com.aiplatform.appstudio.application.result.ChatMessageHistoryResult;
import com.aiplatform.appstudio.application.result.ChatMessageResult;
import com.aiplatform.appstudio.application.result.ChatSessionSummaryResult;
import com.aiplatform.appstudio.application.result.CitationResult;
import com.aiplatform.appstudio.application.result.CreateAiApplicationResult;
import com.aiplatform.appstudio.application.result.CreateChatSessionResult;
import com.aiplatform.appstudio.application.result.DebugEvidenceResult;
import com.aiplatform.appstudio.application.result.PublishPreflightResult;
import com.aiplatform.appstudio.application.result.RetrievalEvidenceResult;
import com.aiplatform.appstudio.domain.AiApplication;
import com.aiplatform.appstudio.domain.AiApplicationType;
import com.aiplatform.appstudio.domain.ApplicationPublicationStatus;
import com.aiplatform.appstudio.domain.ChatMessage;
import com.aiplatform.appstudio.domain.ChatRole;
import com.aiplatform.appstudio.domain.ChatSession;
import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.debug.DebugEvidenceView;
import com.aiplatform.appstudio.domain.port.AiApplicationRepository;
import com.aiplatform.appstudio.domain.port.AppPublishPreflightRepository;
import com.aiplatform.appstudio.domain.port.ApplicationReleaseRepository;
import com.aiplatform.appstudio.domain.port.ChatSessionRepository;
import com.aiplatform.appstudio.domain.port.chat.ChatCompletionPort;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionRequest;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionResult;
import com.aiplatform.appstudio.domain.port.debug.AppStudioDebugRunQueryPort;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunDetail;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunEvidenceSnapshot;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunQueryFilter;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSnapshot;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunSummary;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeBaseAccessCheckPort;
import com.aiplatform.appstudio.domain.port.retrieval.KnowledgeRetrievalPort;
import com.aiplatform.appstudio.domain.publish.AppPublishPreflightDecision;
import com.aiplatform.appstudio.domain.publish.AppPublishPreflightPolicy;
import com.aiplatform.appstudio.domain.publish.AppPublishPreflightResult;
import com.aiplatform.appstudio.domain.release.AiApplicationRelease;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseConflictException;
import com.aiplatform.appstudio.domain.release.ApplicationReleaseOperation;
import com.aiplatform.appstudio.presentation.permission.AppStudioPermissionCatalog;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckItem;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeBaseAccessCheckResponse;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalCitation;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalRequest;
import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalResponse;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.AuthorizationRequestTarget;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.kernel.RequestableAuthorizationException;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.model.PermissionCode;
import com.aiplatform.shared.security.model.ResourceAclSubject;
import com.aiplatform.shared.security.port.PermissionCheckPort;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import com.aiplatform.shared.security.port.ResourceAclCommandPort;
import com.aiplatform.shared.security.port.ResourceAudienceQueryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * app-studio 应用服务：编排 AI 应用、聊天会话以及知识问答闭环。
 */
@Service
public class AppStudioApplicationService {
    private static final PermissionCode MODEL_USE_PERMISSION = AppStudioPermissionCatalog.MODEL_USE;
    private static final String APPLICATION_RESOURCE_TYPE = "AI_APPLICATION";
    private static final String MODEL_USE_CASE = "RAG_QUERY";
    private static final int APPLICATION_CATALOG_LIMIT = 200;
    private static final int APPLICATION_CATALOG_PROBE_LIMIT = APPLICATION_CATALOG_LIMIT + 1;
    private static final String INVALIDATED_LEGACY_PREFLIGHT_FINGERPRINT = "0".repeat(64);

    private final AiApplicationRepository aiApplicationRepository;
    private final ApplicationReleaseRepository applicationReleaseRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final KnowledgeRetrievalPort knowledgeRetrievalPort;
    private final KnowledgeBaseAccessCheckPort knowledgeBaseAccessCheckPort;
    private final ModelUsagePolicyPort modelUsagePolicyPort;
    private final ChatCompletionPort chatCompletionPort;
    private final ChatRunTransactionService chatRunTransactionService;
    private final AppStudioDebugRunQueryPort debugRunQueryPort;
    private final PermissionCheckPort permissionCheckPort;
    private final RbacAuthorizationQueryPort authorizationQueryPort;
    private final ResourceAclCommandPort resourceAclCommandPort;
    private final ResourceAudienceQueryPort resourceAudienceQueryPort;
    private final AppPublishPreflightRepository publishPreflightRepository;
    private final AuditEventPublisher auditPublisher;
    private final AppPublishPreflightPolicy publishPreflightPolicy;
    private final Clock clock;

    public AppStudioApplicationService(
            AiApplicationRepository aiApplicationRepository,
            ApplicationReleaseRepository applicationReleaseRepository,
            ChatSessionRepository chatSessionRepository,
            KnowledgeRetrievalPort knowledgeRetrievalPort,
            KnowledgeBaseAccessCheckPort knowledgeBaseAccessCheckPort,
            ModelUsagePolicyPort modelUsagePolicyPort,
            ChatCompletionPort chatCompletionPort,
            ChatRunTransactionService chatRunTransactionService,
            AppStudioDebugRunQueryPort debugRunQueryPort,
            PermissionCheckPort permissionCheckPort,
            RbacAuthorizationQueryPort authorizationQueryPort,
            ResourceAclCommandPort resourceAclCommandPort,
            ResourceAudienceQueryPort resourceAudienceQueryPort,
            AppPublishPreflightRepository publishPreflightRepository,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.aiApplicationRepository = Objects.requireNonNull(aiApplicationRepository, "应用仓储不能为空");
        this.applicationReleaseRepository = Objects.requireNonNull(
                applicationReleaseRepository,
                "应用发布版本仓储不能为空"
        );
        this.chatSessionRepository = Objects.requireNonNull(chatSessionRepository, "会话仓储不能为空");
        this.knowledgeRetrievalPort = Objects.requireNonNull(knowledgeRetrievalPort, "检索端口不能为空");
        this.knowledgeBaseAccessCheckPort = Objects.requireNonNull(knowledgeBaseAccessCheckPort, "知识库访问检查端口不能为空");
        this.modelUsagePolicyPort = Objects.requireNonNull(modelUsagePolicyPort, "模型策略端口不能为空");
        this.chatCompletionPort = Objects.requireNonNull(chatCompletionPort, "聊天生成端口不能为空");
        this.chatRunTransactionService = Objects.requireNonNull(chatRunTransactionService, "聊天运行事务服务不能为空");
        this.debugRunQueryPort = Objects.requireNonNull(debugRunQueryPort, "调试运行查询端口不能为空");
        this.permissionCheckPort = Objects.requireNonNull(permissionCheckPort, "权限校验端口不能为空");
        this.authorizationQueryPort = Objects.requireNonNull(authorizationQueryPort, "RBAC 授权查询端口不能为空");
        this.resourceAclCommandPort = Objects.requireNonNull(resourceAclCommandPort, "资源 ACL 命令端口不能为空");
        this.resourceAudienceQueryPort = Objects.requireNonNull(resourceAudienceQueryPort, "资源受众查询端口不能为空");
        this.publishPreflightRepository = Objects.requireNonNull(publishPreflightRepository, "发布预检仓储不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布器不能为空");
        this.publishPreflightPolicy = new AppPublishPreflightPolicy();
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public List<AiApplicationCatalogResult> listApplications(TenantId tenantId, String actorId) {
        TenantId normalizedTenantId = requireValue(tenantId, "租户 ID");
        String normalizedActorId = requireText(actorId, "操作者 ID");
        Set<PermissionCode> permissionCodes = enforceTenantPermission(
                normalizedTenantId,
                normalizedActorId,
                AppStudioPermissionCatalog.APPLICATION_READ,
                "当前主体无权查看 AI 应用"
        );
        List<AiApplication> applications = aiApplicationRepository.findCatalogWindowByTenant(
                normalizedTenantId,
                APPLICATION_CATALOG_PROBE_LIMIT
        );
        if (applications.size() > APPLICATION_CATALOG_LIMIT) {
            publishSecurityAudit(
                    normalizedTenantId,
                    normalizedActorId,
                    "AI_APPLICATION_CATALOG",
                    normalizedTenantId.value(),
                    "SECURITY_APP_CATALOG_WINDOW_EXCEEDED",
                    "MEDIUM",
                    effectiveTraceId(TraceContext.currentTraceId()),
                    Map.of("limit", APPLICATION_CATALOG_LIMIT, "observedAtLeast", applications.size())
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.CATALOG_WINDOW_EXCEEDED,
                    "当前租户应用数量超过 200，请联系平台管理员启用分页目录后重试"
            );
        }
        Map<String, AiApplicationRelease> activeReleases = applicationReleaseRepository
                .findActiveByApplicationIds(
                        normalizedTenantId,
                        applications.stream().map(AiApplication::applicationId).toList()
                );
        return applications.stream()
                .map(application -> visibleCatalogDetail(
                        normalizedActorId,
                        permissionCodes,
                        application,
                        activeReleases.get(application.applicationId())
                ))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @Transactional
    public CreateAiApplicationResult createApplication(CreateAiApplicationCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        AiApplicationType type = requireValue(command.applicationType(), "应用类型");
        Set<PermissionCode> permissionCodes = enforceTenantPermission(
                tenantId,
                actorId,
                AppStudioPermissionCatalog.APPLICATION_CREATE,
                "当前主体无权创建 AI 应用"
        );
        String applicationId = aiApplicationRepository.nextApplicationId();
        Instant now = Instant.now(clock);
        AiApplication application = new AiApplication(
                tenantId,
                applicationId,
                type,
                requireText(command.name(), "应用名称"),
                requireText(command.description(), "应用描述"),
                requireText(command.modelConfigId(), "模型配置 ID"),
                deduplicate(command.knowledgeBaseIds(), "知识库 ID"),
                List.of(),
                Set.of(actorId),
                requireText(command.systemPrompt(), "系统提示词"),
                ApplicationPublicationStatus.DRAFT,
                now,
                now
        );
        AiApplication saved = aiApplicationRepository.save(application);
        return new CreateAiApplicationResult(
                saved.applicationId(),
                "APP-" + saved.applicationId(),
                saved.name(),
                saved.modelConfigId(),
                saved.knowledgeBaseIds(),
                saved.publicationStatus().name(),
                saved.createdAt(),
                ApplicationAccessMode.OWNER,
                ApplicationCapabilitiesResult.owner(
                        permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_UPDATE),
                        permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_PUBLISH),
                        permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL)
                )
        );
    }

    public CreateChatSessionResult createSession(CreateChatSessionCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        AiApplicationRelease release = loadUsableRelease(
                tenantId,
                requireText(command.applicationId(), "应用 ID"),
                actorId,
                null
        );
        Instant now = Instant.now(clock);
        ChatSession session = new ChatSession(
                tenantId,
                chatSessionRepository.nextSessionId(),
                release.applicationId(),
                release.releaseId(),
                release.version(),
                actorId,
                normalizeSessionTitle(command.title()),
                now,
                now
        );
        ChatSession saved = chatSessionRepository.save(session);
        return new CreateChatSessionResult(
                saved.sessionId(),
                saved.applicationId(),
                saved.releaseId(),
                saved.releaseVersion(),
                saved.userId(),
                saved.title(),
                saved.createdAt(),
                saved.lastMessageAt()
        );
    }

    public List<ChatSessionSummaryResult> listSessions(TenantId tenantId, String actorId, String applicationId) {
        AiApplicationRelease activeRelease = loadUsableRelease(tenantId, applicationId, actorId, null);
        return chatSessionRepository.listSessions(tenantId, activeRelease.applicationId(), actorId).stream()
                .map(session -> new ChatSessionSummaryResult(
                        session.sessionId(),
                        session.applicationId(),
                        session.releaseId(),
                        session.releaseVersion(),
                        !matchesRelease(session, activeRelease),
                        session.userId(),
                        session.title(),
                        session.createdAt(),
                        session.lastMessageAt()))
                .toList();
    }

    public List<ChatMessageHistoryResult> listMessages(TenantId tenantId, String actorId, String sessionId) {
        ChatSession session = requireOwnedSession(tenantId, actorId, sessionId);
        return chatSessionRepository.listMessages(tenantId, sessionId).stream()
                .map(message -> new ChatMessageHistoryResult(
                        message.messageId(),
                        message.sessionId(),
                        session.applicationId(),
                        message.role().name(),
                        message.content(),
                        message.citations().stream().map(CitationResult::from).toList(),
                        message.citations().size(),
                        message.modelInvocationLogId(),
                        message.traceId(),
                        message.createdAt()))
                .toList();
    }

    public List<AppStudioDebugRunSummary> listDebugRuns(
            TenantId tenantId,
            String actorId,
            String applicationId,
            String sessionId,
            String traceId,
            String modelInvocationLogId,
            String invocationStatus
    ) {
        AiApplication application = loadUsableApplication(tenantId, applicationId, actorId);
        return debugRunQueryPort.listDebugRuns(new AppStudioDebugRunQueryFilter(
                tenantId,
                application.applicationId(),
                requireText(actorId, "操作者 ID"),
                normalizeOptionalText(sessionId),
                normalizeOptionalText(traceId),
                normalizeOptionalText(modelInvocationLogId),
                normalizeOptionalText(invocationStatus),
                50
        ));
    }

    public DebugEvidenceResult getDebugEvidence(GetDebugEvidenceCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        if (command.view() == DebugEvidenceView.SUMMARY) {
            AiApplication application = loadUsableApplication(tenantId, command.applicationId(), actorId);
            AppStudioDebugRunDetail detail = findDebugEvidence(
                    tenantId,
                    application.applicationId(),
                    actorId,
                    command.assistantMessageId()
            );
            return new DebugEvidenceResult(detail, true, null);
        }

        AiApplication application = aiApplicationRepository.findById(tenantId, command.applicationId())
                .orElseThrow(() -> new ApplicationException(
                        AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                        "调试运行不可见"
        ));
        Set<PermissionCode> permissions = authorizationQueryPort.findPermissionCodes(tenantId, "user", actorId);
        if (!permissions.contains(AppStudioPermissionCatalog.DEBUG_EVIDENCE_READ)
                || !permissions.contains(AppStudioPermissionCatalog.APPLICATION_READ)) {
            publishSecurityAudit(
                    tenantId,
                    actorId,
                    "APP_DEBUG_EVIDENCE",
                    command.assistantMessageId(),
                    "SECURITY_APP_DEBUG_EVIDENCE_DENIED",
                    "HIGH",
                    effectiveTraceId(TraceContext.currentTraceId()),
                    Map.of("reason", "debug_evidence_permission_denied")
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.AUTHORIZATION_DENIED,
                    "当前主体无权查看完整调试证据"
            );
        }
        AppStudioDebugRunDetail detail = findDebugEvidence(
                tenantId,
                application.applicationId(),
                null,
                command.assistantMessageId()
        );
        String auditEventId = publishSecurityAudit(
                tenantId,
                actorId,
                "APP_DEBUG_EVIDENCE",
                command.assistantMessageId(),
                "SECURITY_APP_DEBUG_EVIDENCE_VIEWED",
                "HIGH",
                detail.traceId(),
                Map.of("reason", command.reason(), "view", "full")
        );
        return new DebugEvidenceResult(detail, false, auditEventId);
    }

    private AppStudioDebugRunDetail findDebugEvidence(
            TenantId tenantId,
            String applicationId,
            String userId,
            String assistantMessageId
    ) {
        return debugRunQueryPort.findDebugRunDetail(
                        tenantId,
                        applicationId,
                        userId,
                        requireText(assistantMessageId, "助手消息 ID"))
                .orElseThrow(() -> new ApplicationException(
                        AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                        "调试运行不可见"
                ));
    }

    @Transactional
    public AiApplicationDetailResult updateApplication(UpdateAiApplicationCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        acquireApplicationMutationLock(
                tenantId,
                command.applicationId(),
                AppStudioErrorCodes.RESOURCE_NOT_VISIBLE
        );
        AiApplication existing = loadApplicationRequiringPermission(
                tenantId,
                command.applicationId(),
                actorId,
                AppStudioPermissionCatalog.APPLICATION_UPDATE,
                "当前主体无权更新该 AI 应用"
        );
        Instant now = Instant.now(clock);
        AiApplication updated = new AiApplication(
                tenantId,
                existing.applicationId(),
                existing.type(),
                requireText(command.name(), "应用名称"),
                command.description(),
                requireText(command.modelConfigId(), "模型配置 ID"),
                deduplicate(command.knowledgeBaseIds(), "知识库 ID"),
                existing.toolIds(),
                existing.visiblePrincipalIds(),
                requireText(command.systemPrompt(), "系统提示词"),
                existing.publicationStatus(),
                existing.createdAt(),
                now
        );
        return toDetailResult(aiApplicationRepository.save(updated));
    }

    @Transactional
    public ApplicationAccessPolicyResult replaceApplicationAccessPolicy(
            ReplaceApplicationAccessPolicyCommand command
    ) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        acquireApplicationMutationLock(
                tenantId,
                command.applicationId(),
                AppStudioErrorCodes.RESOURCE_NOT_VISIBLE
        );
        AiApplication application = loadApplicationRequiringPermission(
                tenantId,
                command.applicationId(),
                actorId,
                AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL,
                "当前主体无权维护应用授权"
        );
        Set<ResourceAclSubject> subjects = new LinkedHashSet<>();
        command.userIds().forEach(id -> subjects.add(new ResourceAclSubject("user", id)));
        command.roleIds().forEach(id -> subjects.add(new ResourceAclSubject("role", id)));
        resourceAclCommandPort.replaceAllowingSubjects(
                tenantId,
                APPLICATION_RESOURCE_TYPE,
                application.applicationId(),
                AppStudioPermissionCatalog.APPLICATION_USE,
                Set.copyOf(subjects),
                actorId
        );
        Instant updatedAt = Instant.now(clock);
        aiApplicationRepository.save(new AiApplication(
                tenantId,
                application.applicationId(),
                application.type(),
                application.name(),
                application.description(),
                application.modelConfigId(),
                application.knowledgeBaseIds(),
                application.toolIds(),
                application.visiblePrincipalIds(),
                application.systemPrompt(),
                application.publicationStatus(),
                application.createdAt(),
                updatedAt
        ));
        String auditEventId = publishSecurityAudit(
                tenantId,
                actorId,
                APPLICATION_RESOURCE_TYPE,
                application.applicationId(),
                "APP_APPLICATION_ACCESS_POLICY_REPLACED",
                "HIGH",
                effectiveTraceId(TraceContext.currentTraceId()),
                Map.of(
                        "reason", command.reason(),
                        "authorizedUserCount", command.userIds().size(),
                        "authorizedRoleCount", command.roleIds().size()
                )
        );
        return new ApplicationAccessPolicyResult(
                application.applicationId(),
                command.userIds().size(),
                command.roleIds().size(),
                auditEventId,
                updatedAt
        );
    }

    @Transactional
    public PublishPreflightResult runPublishPreflight(RunPublishPreflightCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        AiApplication application = loadApplicationRequiringPermission(
                tenantId,
                command.applicationId(),
                actorId,
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                "当前主体无权发布该 AI 应用"
        );
        Set<String> audience = releaseAudience(tenantId, application);
        String candidateFingerprint = publishCandidateFingerprint(application, audience);
        AppPublishPreflightResult existing = publishPreflightRepository.findByRequestId(
                tenantId,
                application.applicationId(),
                command.publishRequestId()
        ).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.checkedBy(), actorId)
                    || !matchesPublishCandidate(existing.candidateFingerprint(), candidateFingerprint)) {
                throw new ApplicationException(
                        AppStudioErrorCodes.PUBLISH_BLOCKED,
                        "发布候选或检查主体与已有预检结果不一致，请使用新的 publishRequestId 重新检查"
                );
            }
            return PublishPreflightResult.from(existing);
        }

        AppPublishPreflightDecision decision = evaluatePublishPreflight(
                tenantId,
                actorId,
                application,
                audience
        );
        Instant checkedAt = Instant.now(clock);
        AppPublishPreflightResult saved = publishPreflightRepository.save(new AppPublishPreflightResult(
                tenantId,
                application.applicationId(),
                command.publishRequestId(),
                candidateFingerprint,
                decision.passed(),
                decision.blockers(),
                decision.warnings(),
                effectiveTraceId(TraceContext.currentTraceId()),
                actorId,
                checkedAt
        ));
        if (!Objects.equals(saved.checkedBy(), actorId)
                || !matchesPublishCandidate(saved.candidateFingerprint(), candidateFingerprint)) {
            throw new ApplicationException(
                    AppStudioErrorCodes.PUBLISH_BLOCKED,
                    "publishRequestId 已绑定其他发布候选，请使用新的 publishRequestId 重新检查"
            );
        }
        if (!saved.passed()) {
            publishAppAudit(
                    tenantId,
                    actorId,
                    application.applicationId(),
                    "SECURITY_APP_PUBLISH_BLOCKED",
                    "HIGH",
                    saved.traceId(),
                    Map.of(
                            "publishRequestId", saved.publishRequestId(),
                            "blockerCodes", saved.blockers().stream().map(issue -> issue.code()).toList()
                    )
            );
        }
        return PublishPreflightResult.from(saved);
    }

    private AppPublishPreflightDecision evaluatePublishPreflight(
            TenantId tenantId,
            String actorId,
            AiApplication application,
            Set<String> audience
    ) {
        Set<String> roleCodes = authorizationQueryPort.findRoleCodes(tenantId, "user", actorId);
        Set<PermissionCode> permissionCodes = authorizationQueryPort.findPermissionCodes(tenantId, "user", actorId);
        KnowledgeBaseAccessCheckResponse knowledgeAccess = checkKnowledgeAccessForAudience(
                tenantId,
                application,
                audience
        );
        AppModelUsageDecision modelDecision = modelUsagePolicyPort.decide(new AppModelUsageRequest(
                tenantId,
                actorId,
                "user",
                roleCodes,
                permissionCodes,
                application.applicationId(),
                MODEL_USE_CASE,
                "CHAT",
                application.modelConfigId(),
                Map.of("publishPreflight", true),
                0,
                0
        ));
        if (modelDecision == null) {
            modelDecision = new AppModelUsageDecision(
                    false,
                    "model_policy_projection_missing",
                    "模型使用策略投影不存在或未同步",
                    null,
                    Map.of()
            );
        }
        return publishPreflightPolicy.evaluate(application, knowledgeAccess, modelDecision);
    }

    private KnowledgeBaseAccessCheckResponse checkKnowledgeAccessForAudience(
            TenantId tenantId,
            AiApplication application
    ) {
        Set<String> audience = new LinkedHashSet<>(application.visiblePrincipalIds());
        resourceAudienceQueryPort.findAllowedUserIds(
                        tenantId,
                        APPLICATION_RESOURCE_TYPE,
                        application.applicationId(),
                        AppStudioPermissionCatalog.APPLICATION_USE
                ).stream()
                .filter(principalId -> permissionCheckPort.canAccess(
                        tenantId,
                        principalId,
                        AppStudioPermissionCatalog.APPLICATION_USE,
                        APPLICATION_RESOURCE_TYPE,
                        application.applicationId()
                ))
                .forEach(audience::add);
        return checkKnowledgeAccessForAudience(tenantId, application, audience);
    }

    private KnowledgeBaseAccessCheckResponse checkKnowledgeAccessForAudience(
            TenantId tenantId,
            AiApplication application,
            Set<String> audience
    ) {
        if (application.knowledgeBaseIds().isEmpty()) {
            return new KnowledgeBaseAccessCheckResponse(List.of());
        }
        if (audience.isEmpty()) {
            return new KnowledgeBaseAccessCheckResponse(application.knowledgeBaseIds().stream()
                    .map(knowledgeBaseId -> KnowledgeBaseAccessCheckItem.denied(
                            knowledgeBaseId,
                            "knowledge_access_audience_empty"
                    ))
                    .toList());
        }
        List<KnowledgeBaseAccessCheckResponse> audienceChecks = audience.stream()
                .map(principalId -> knowledgeBaseAccessCheckPort.check(new KnowledgeBaseAccessCheckRequest(
                        tenantId,
                        "user",
                        principalId,
                        application.knowledgeBaseIds()
                )))
                .toList();
        List<KnowledgeBaseAccessCheckItem> combined = new ArrayList<>();
        for (String knowledgeBaseId : application.knowledgeBaseIds()) {
            KnowledgeBaseAccessCheckItem denied = audienceChecks.stream()
                    .map(check -> check.items().stream()
                            .filter(item -> knowledgeBaseId.equals(item.knowledgeBaseId()))
                            .findFirst()
                            .orElse(KnowledgeBaseAccessCheckItem.denied(
                                    knowledgeBaseId,
                                    "knowledge_access_check_incomplete"
                            )))
                    .filter(item -> !item.accessible())
                    .findFirst()
                    .orElse(null);
            combined.add(denied == null
                    ? KnowledgeBaseAccessCheckItem.allowed(knowledgeBaseId)
                    : denied);
        }
        return new KnowledgeBaseAccessCheckResponse(combined);
    }

    @Transactional
    public AiApplicationDetailResult publishApplication(PublishAiApplicationCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        AiApplication authorized = loadApplicationRequiringPermission(
                tenantId,
                command.applicationId(),
                actorId,
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                "当前主体无权发布该 AI 应用"
        );
        Optional<AiApplicationDetailResult> committedReplay = findCommittedPublishReplay(
                tenantId,
                authorized.applicationId(),
                command.publishRequestId()
        );
        if (committedReplay.isPresent()) {
            return committedReplay.get();
        }
        acquireApplicationMutationLock(
                tenantId,
                authorized.applicationId(),
                AppStudioErrorCodes.PUBLISH_BLOCKED
        );
        authorized = loadApplicationRequiringPermission(
                tenantId,
                authorized.applicationId(),
                actorId,
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                "当前主体无权发布该 AI 应用"
        );
        committedReplay = findCommittedPublishReplay(
                tenantId,
                authorized.applicationId(),
                command.publishRequestId()
        );
        if (committedReplay.isPresent()) {
            return committedReplay.get();
        }
        PublishPreflightResult preflight;
        try {
            preflight = runPublishPreflight(new RunPublishPreflightCommand(
                    tenantId,
                    actorId,
                    authorized.applicationId(),
                    command.publishRequestId()
            ));
        } catch (ApplicationException failure) {
            if (!AppStudioErrorCodes.PUBLISH_BLOCKED.equals(failure.errorCode())) {
                throw failure;
            }
            committedReplay = findCommittedPublishReplay(
                    tenantId,
                    authorized.applicationId(),
                    command.publishRequestId()
            );
            if (committedReplay.isPresent()) {
                return committedReplay.get();
            }
            throw failure;
        }
        if (!preflight.passed()) {
            committedReplay = findCommittedPublishReplay(
                    tenantId,
                    authorized.applicationId(),
                    command.publishRequestId()
            );
            if (committedReplay.isPresent()) {
                return committedReplay.get();
            }
            throw new ApplicationException(
                    AppStudioErrorCodes.PUBLISH_BLOCKED,
                    String.join(",", preflight.blockers().stream().map(issue -> issue.code()).toList())
            );
        }
        AiApplication existing = aiApplicationRepository.findById(tenantId, authorized.applicationId())
                .orElseThrow(() -> new ApplicationException(
                        AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                        "AI 应用不可见"
                ));
        Set<String> allowedUsers = releaseAudience(tenantId, existing);
        String currentCandidateFingerprint = publishCandidateFingerprint(existing, allowedUsers);
        if (!matchesPublishCandidate(
                preflight.candidateFingerprint(),
                currentCandidateFingerprint
        )) {
            committedReplay = findCommittedPublishReplay(
                    tenantId,
                    existing.applicationId(),
                    command.publishRequestId()
            );
            if (committedReplay.isPresent()) {
                return committedReplay.get();
            }
            publishAppAudit(
                    tenantId,
                    actorId,
                    existing.applicationId(),
                    "SECURITY_APP_PUBLISH_BLOCKED",
                    "HIGH",
                    preflight.traceId(),
                    Map.of(
                            "publishRequestId", preflight.publishRequestId(),
                            "reason", "publish_candidate_fingerprint_mismatch"
                    )
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.PUBLISH_BLOCKED,
                    "发布候选在预检后已变化，请使用新的 publishRequestId 重新检查"
            );
        }
        AppPublishPreflightDecision currentDecision = evaluatePublishPreflight(
                tenantId,
                actorId,
                existing,
                allowedUsers
        );
        if (!currentDecision.passed()) {
            committedReplay = findCommittedPublishReplay(
                    tenantId,
                    existing.applicationId(),
                    command.publishRequestId()
            );
            if (committedReplay.isPresent()) {
                return committedReplay.get();
            }
            publishAppAudit(
                    tenantId,
                    actorId,
                    existing.applicationId(),
                    "SECURITY_APP_PUBLISH_BLOCKED",
                    "HIGH",
                    preflight.traceId(),
                    Map.of(
                            "publishRequestId", preflight.publishRequestId(),
                            "reason", "publish_candidate_changed_after_preflight",
                            "blockerCodes", currentDecision.blockers().stream()
                                    .map(issue -> issue.code())
                                    .toList()
                    )
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.PUBLISH_BLOCKED,
                    "发布候选在预检后已变化，请使用新的 publishRequestId 重新检查："
                            + String.join(",", currentDecision.blockers().stream()
                            .map(issue -> issue.code())
                            .toList())
            );
        }
        Instant now = Instant.now(clock);
        ApplicationReleaseRepository.MutationResult mutation = createAndActivateRelease(
                new ApplicationReleaseRepository.MutationCommand(
                        tenantId,
                        existing.applicationId(),
                        command.publishRequestId(),
                        ApplicationReleaseOperation.PUBLISH,
                        null,
                        publishedSnapshot(existing, now),
                        allowedUsers,
                        actorId,
                        command.reason() == null ? "未填写发布原因" : command.reason(),
                        preflight.traceId(),
                        UUID.randomUUID().toString(),
                        now,
                        existing.updatedAt()
                )
        );
        if (mutation.created()) {
            publishAppAudit(
                    tenantId,
                    actorId,
                    existing.applicationId(),
                    "APP_APPLICATION_PUBLISHED",
                    "MEDIUM",
                    preflight.traceId(),
                    Map.of(
                            "publishRequestId", preflight.publishRequestId(),
                            "releaseId", mutation.release().releaseId(),
                            "releaseVersion", mutation.release().version(),
                            "reason", mutation.release().reason()
                    )
            );
        }
        return toDetailResult(mutation.release());
    }

    public List<ApplicationReleaseSummaryResult> listApplicationReleases(
            TenantId tenantId,
            String actorId,
            String applicationId
    ) {
        TenantId normalizedTenantId = requireValue(tenantId, "租户 ID");
        String normalizedActorId = requireText(actorId, "操作者 ID");
        ApplicationGovernanceAccess access = loadApplicationForGovernanceRead(
                normalizedTenantId,
                applicationId,
                normalizedActorId
        );
        AiApplication application = access.application();
        String activeReleaseId = applicationReleaseRepository.findActive(
                normalizedTenantId,
                application.applicationId()
        ).map(AiApplicationRelease::releaseId).orElse(null);
        List<AiApplicationRelease> releases = applicationReleaseRepository.findAll(
                normalizedTenantId,
                application.applicationId()
        );
        Map<String, Long> versionByReleaseId = releases.stream().collect(Collectors.toUnmodifiableMap(
                AiApplicationRelease::releaseId,
                AiApplicationRelease::version
        ));
        return releases.stream()
                .map(release -> new ApplicationReleaseSummaryResult(
                        release.releaseId(),
                        release.version(),
                        release.releaseId().equals(activeReleaseId),
                        release.operation().name(),
                        release.sourceReleaseId(),
                        sourceReleaseVersion(release, versionByReleaseId),
                        release.snapshot().name(),
                        release.snapshot().type().name(),
                        release.snapshot().modelConfigId(),
                        release.snapshot().knowledgeBaseIds(),
                        access.governanceReadOnly()
                                ? null
                                : systemPromptSummary(release.snapshot().systemPrompt()),
                        sha256(release.snapshot().systemPrompt()),
                        release.snapshot().visiblePrincipalIds().size(),
                        release.allowedUserIds().size(),
                        release.publishedBy(),
                        release.reason(),
                        release.traceId(),
                        release.publishedAt(),
                        release.publishedAt()
                ))
                .toList();
    }

    public ApplicationReleaseDetailResult getApplicationRelease(
            TenantId tenantId,
            String actorId,
            String applicationId,
            String releaseId
    ) {
        TenantId normalizedTenantId = requireValue(tenantId, "租户 ID");
        String normalizedActorId = requireText(actorId, "操作者 ID");
        ApplicationGovernanceAccess access = loadApplicationForGovernanceRead(
                normalizedTenantId,
                applicationId,
                normalizedActorId
        );
        AiApplication application = access.application();
        AiApplicationRelease release = applicationReleaseRepository.findById(
                normalizedTenantId,
                application.applicationId(),
                requireText(releaseId, "发布版本 ID")
        ).orElseThrow(() -> new ApplicationException(
                AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                "应用发布版本不可见"
        ));
        boolean active = applicationReleaseRepository.findActive(
                        normalizedTenantId,
                        application.applicationId()
                )
                .map(activeRelease -> activeRelease.releaseId().equals(release.releaseId()))
                .orElse(false);
        AiApplication snapshot = release.snapshot();
        return new ApplicationReleaseDetailResult(
                release.releaseId(),
                release.version(),
                active,
                release.operation().name(),
                release.sourceReleaseId(),
                findSourceReleaseVersion(release),
                release.applicationId(),
                snapshot.name(),
                snapshot.description(),
                snapshot.type().name(),
                snapshot.modelConfigId(),
                snapshot.knowledgeBaseIds(),
                snapshot.toolIds(),
                access.governanceReadOnly() ? null : snapshot.systemPrompt(),
                sha256(snapshot.systemPrompt()),
                snapshot.visiblePrincipalIds().size(),
                release.allowedUserIds().size(),
                release.publishedBy(),
                release.reason(),
                release.traceId(),
                snapshot.createdAt(),
                release.publishedAt()
        );
    }

    @Transactional
    public AiApplicationDetailResult rollbackApplication(RollbackApplicationReleaseCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        acquireApplicationMutationLock(
                tenantId,
                command.applicationId(),
                AppStudioErrorCodes.PUBLISH_BLOCKED
        );
        AiApplication application = loadApplicationRequiringPermission(
                tenantId,
                command.applicationId(),
                actorId,
                AppStudioPermissionCatalog.APPLICATION_PUBLISH,
                "当前主体无权回滚该 AI 应用"
        );
        AiApplicationRelease duplicate = applicationReleaseRepository.findByRequestId(
                tenantId,
                application.applicationId(),
                command.rollbackRequestId()
        ).orElse(null);
        if (duplicate != null) {
            requireIdempotentReleaseMatches(
                    duplicate,
                    ApplicationReleaseOperation.ROLLBACK,
                    command.targetReleaseId()
            );
            return toDetailResult(duplicate);
        }
        AiApplicationRelease target = applicationReleaseRepository.findById(
                tenantId,
                application.applicationId(),
                command.targetReleaseId()
        ).orElseThrow(() -> new ApplicationException(
                AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                "应用发布版本不可见"
        ));
        validateRollbackCandidate(tenantId, actorId, target);
        Instant now = Instant.now(clock);
        String traceId = effectiveTraceId(TraceContext.currentTraceId());
        ApplicationReleaseRepository.MutationResult mutation = createAndActivateRelease(
                new ApplicationReleaseRepository.MutationCommand(
                        tenantId,
                        application.applicationId(),
                        command.rollbackRequestId(),
                        ApplicationReleaseOperation.ROLLBACK,
                        target.releaseId(),
                        publishedSnapshot(target.snapshot(), now),
                        target.allowedUserIds(),
                        actorId,
                        command.reason(),
                        traceId,
                        UUID.randomUUID().toString(),
                        now,
                        application.updatedAt()
                )
        );
        if (mutation.created()) {
            publishAppAudit(
                    tenantId,
                    actorId,
                    application.applicationId(),
                    "APP_APPLICATION_RELEASE_ROLLED_BACK",
                    "HIGH",
                    traceId,
                    Map.of(
                            "rollbackRequestId", command.rollbackRequestId(),
                            "targetReleaseId", target.releaseId(),
                            "releaseId", mutation.release().releaseId(),
                            "releaseVersion", mutation.release().version(),
                            "reason", command.reason()
                    )
            );
        }
        return toDetailResult(mutation.release());
    }

    private void validateRollbackCandidate(
            TenantId tenantId,
            String actorId,
            AiApplicationRelease target
    ) {
        Set<String> roleCodes = authorizationQueryPort.findRoleCodes(tenantId, "user", actorId);
        Set<PermissionCode> permissionCodes = authorizationQueryPort.findPermissionCodes(tenantId, "user", actorId);
        AppModelUsageDecision modelDecision = modelUsagePolicyPort.decide(new AppModelUsageRequest(
                tenantId,
                actorId,
                "user",
                roleCodes,
                permissionCodes,
                target.applicationId(),
                MODEL_USE_CASE,
                "CHAT",
                target.snapshot().modelConfigId(),
                Map.of(
                        "rollbackPreflight", true,
                        "targetReleaseId", target.releaseId(),
                        "targetReleaseVersion", target.version()
                ),
                0,
                0
        ));
        if (modelDecision == null) {
            modelDecision = new AppModelUsageDecision(
                    false,
                    "model_policy_projection_missing",
                    "模型使用策略投影不存在或未同步",
                    null,
                    Map.of()
            );
        }
        AppPublishPreflightDecision decision = publishPreflightPolicy.evaluate(
                target.snapshot(),
                checkKnowledgeAccessForAudience(tenantId, target.snapshot(), target.allowedUserIds()),
                modelDecision
        );
        if (decision.passed()) {
            return;
        }
        String traceId = effectiveTraceId(TraceContext.currentTraceId());
        publishAppAudit(
                tenantId,
                actorId,
                target.applicationId(),
                "SECURITY_APP_ROLLBACK_BLOCKED",
                "HIGH",
                traceId,
                Map.of(
                        "targetReleaseId", target.releaseId(),
                        "targetReleaseVersion", target.version(),
                        "blockerCodes", decision.blockers().stream().map(issue -> issue.code()).toList()
                )
        );
        throw new ApplicationException(
                AppStudioErrorCodes.PUBLISH_BLOCKED,
                String.join(",", decision.blockers().stream().map(issue -> issue.code()).toList())
        );
    }

    public ChatMessageResult sendMessage(SendChatMessageCommand command) {
        TenantId tenantId = requireValue(command.tenantId(), "租户 ID");
        String actorId = requireText(command.actorId(), "操作者 ID");
        String sessionId = requireText(command.sessionId(), "会话 ID");
        ChatSession session = requireOwnedSession(tenantId, actorId, sessionId);
        AiApplication application = loadUsableRelease(
                tenantId,
                session.applicationId(),
                actorId,
                session
        ).snapshot();
        Claim claim = chatRunTransactionService.claim(command, application.applicationId());
        if (claim.state() == ClaimState.COMPLETED) {
            return claim.completedResult();
        }
        if (claim.state() == ClaimState.RESULT_RECEIVED) {
            try {
                chatRunTransactionService.complete(claim.ownership(), claim.finalization());
            } catch (RuntimeException exception) {
                recordFinalizationFailureSafely(claim.ownership(), exception);
                throw exception;
            }
            return claim.finalization().result();
        }
        if (claim.state() != ClaimState.ACQUIRED) {
            throw claimFailure(claim);
        }

        Ownership ownership = claim.ownership();
        try {
            return executeClaimedChatRun(command, tenantId, actorId, sessionId, application, ownership);
        } catch (FinalizationPendingException pendingException) {
            throw pendingException.cause();
        } catch (ModelDispatchedException dispatchedException) {
            RuntimeException cause = dispatchedException.cause();
            settleFailedRun(ownership, true, cause);
            throw cause;
        } catch (RuntimeException exception) {
            settleFailedRun(ownership, false, exception);
            throw exception;
        }
    }

    private ChatMessageResult executeClaimedChatRun(
            SendChatMessageCommand command,
            TenantId tenantId,
            String actorId,
            String sessionId,
            AiApplication application,
            Ownership ownership
    ) {
        Instant now = Instant.now(clock);
        String traceId = effectiveTraceId(command.traceId());
        String userContent = requireText(command.message(), "消息内容");
        KnowledgeRetrievalResponse retrievalResult = knowledgeRetrievalPort.retrieve(new KnowledgeRetrievalRequest(
                tenantId,
                actorId,
                "user",
                List.of(actorId),
                application.knowledgeBaseIds(),
                userContent,
                "balanced",
                5,
                0.0,
                true
        ));

        if (!retrievalResult.answerable()) {
            ChatMessage userMessage = newUserMessage(
                    tenantId, sessionId, command.clientMessageId(), userContent, traceId, now
            );
            ChatMessage assistantMessage = new ChatMessage(
                    tenantId,
                    chatSessionRepository.nextMessageId(),
                    sessionId,
                    ChatRole.ASSISTANT,
                    retrievalResult.noAnswerReason(),
                    List.of(),
                    null,
                    traceId,
                    now
            );
            ChatMessageResult result = new ChatMessageResult(
                    userMessage.messageId(),
                    assistantMessage.messageId(),
                    sessionId,
                    application.applicationId(),
                    assistantMessage.content(),
                    false,
                    List.of(),
                    retrievalResult.evidences().stream().map(RetrievalEvidenceResult::from).toList(),
                    retrievalResult.noAnswerReason(),
                    retrievalResult.filteredCitationCount(),
                    assistantMessage.createdAt(),
                    Map.of(
                            "mode", "retrieval-fail-closed",
                            "traceId", traceId,
                            "decisionSource", "knowledge-retrieval-fail-closed"
                    )
            );
            return completeChatRun(ownership, userMessage, assistantMessage, retrievalResult.evidences(), result);
        }

        Set<PermissionCode> permissionCodes = authorizationQueryPort.findPermissionCodes(tenantId, "user", actorId);
        if (!permissionCodes.contains(MODEL_USE_PERMISSION)) {
            throw new ApplicationException(
                    AppStudioErrorCodes.AUTHORIZATION_DENIED,
                    "当前主体无权调用 AI 应用模型"
            );
        }
        Set<String> roleCodes = authorizationQueryPort.findRoleCodes(tenantId, "user", actorId);
        AppModelUsageDecision decision = modelUsagePolicyPort.decide(new AppModelUsageRequest(
                tenantId,
                actorId,
                "user",
                roleCodes,
                permissionCodes,
                application.applicationId(),
                MODEL_USE_CASE,
                "CHAT",
                application.modelConfigId(),
                Map.of("traceId", traceId),
                userContent.length(),
                2048
        ));
        if (decision == null) {
            decision = new AppModelUsageDecision(
                    false,
                    "model_policy_projection_missing",
                    "模型使用策略投影不存在或未同步",
                    null,
                    Map.of()
            );
        }
        if (!decision.allowed()) {
            publishSecurityAudit(
                    tenantId,
                    actorId,
                    APPLICATION_RESOURCE_TYPE,
                    application.applicationId(),
                    "SECURITY_APP_MODEL_POLICY_DENIED",
                    "HIGH",
                    traceId,
                    Map.of("reason", decision.reason(), "clientMessageId", command.clientMessageId())
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.MODEL_POLICY_DENIED,
                    decision.reason()
            );
        }
        ChatMessage userMessage = newUserMessage(
                tenantId, sessionId, command.clientMessageId(), userContent, traceId, now
        );
        List<Citation> retrievalCitations = retrievalResult.citations().stream()
                .map(AppStudioApplicationService::toDomainCitation)
                .toList();
        ChatCompletionRequest completionRequest = new ChatCompletionRequest(
                tenantId,
                application.applicationId(),
                actorId,
                traceId,
                command.clientMessageId(),
                application.systemPrompt(),
                userMessage.content(),
                retrievalCitations,
                decision.runtimeDescriptor()
        );
        chatRunTransactionService.markModelDispatched(ownership);
        try {
            return executeDispatchedModelCall(
                    tenantId,
                    sessionId,
                    application,
                    ownership,
                    now,
                    traceId,
                    userMessage,
                    retrievalResult,
                    retrievalCitations,
                    completionRequest
            );
        } catch (FinalizationPendingException pendingException) {
            throw pendingException;
        } catch (RuntimeException exception) {
            throw new ModelDispatchedException(exception);
        }
    }

    private ChatMessageResult executeDispatchedModelCall(
            TenantId tenantId,
            String sessionId,
            AiApplication application,
            Ownership ownership,
            Instant now,
            String traceId,
            ChatMessage userMessage,
            KnowledgeRetrievalResponse retrievalResult,
            List<Citation> retrievalCitations,
            ChatCompletionRequest completionRequest
    ) {
        ChatCompletionResult completionResult = chatCompletionPort.complete(completionRequest);
        if (!completionResult.answerable()) {
            ChatMessage assistantMessage = new ChatMessage(
                    tenantId,
                    chatSessionRepository.nextMessageId(),
                    sessionId,
                    ChatRole.ASSISTANT,
                    completionResult.answer(),
                    List.of(),
                    completionResult.modelInvocationLogId(),
                    traceId,
                    now
            );
            ChatMessageResult result = new ChatMessageResult(
                    userMessage.messageId(),
                    assistantMessage.messageId(),
                    sessionId,
                    application.applicationId(),
                    assistantMessage.content(),
                    false,
                    List.of(),
                    retrievalResult.evidences().stream().map(RetrievalEvidenceResult::from).toList(),
                    completionResult.failureReason(),
                    retrievalResult.filteredCitationCount(),
                    assistantMessage.createdAt(),
                    completionResult.auditAttributes().entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, entry -> (Object) entry.getValue()))
            );
            return completeChatRun(ownership, userMessage, assistantMessage, retrievalResult.evidences(), result);
        }
        String groundedAnswer = finalizeGroundedAnswer(completionResult.answer(), retrievalCitations);
        ChatMessage assistantMessage = new ChatMessage(
                tenantId,
                chatSessionRepository.nextMessageId(),
                sessionId,
                ChatRole.ASSISTANT,
                groundedAnswer,
                retrievalCitations,
                completionResult.modelInvocationLogId(),
                traceId,
                now
        );
        ChatMessageResult result = new ChatMessageResult(
                userMessage.messageId(),
                assistantMessage.messageId(),
                sessionId,
                application.applicationId(),
                assistantMessage.content(),
                true,
                retrievalCitations.stream().map(CitationResult::from).toList(),
                retrievalResult.evidences().stream().map(RetrievalEvidenceResult::from).toList(),
                null,
                retrievalResult.filteredCitationCount(),
                assistantMessage.createdAt(),
                completionResult.auditAttributes().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> (Object) entry.getValue()))
        );
        return completeChatRun(ownership, userMessage, assistantMessage, retrievalResult.evidences(), result);
    }

    private ChatMessageResult completeChatRun(
            Ownership ownership,
            ChatMessage userMessage,
            ChatMessage assistantMessage,
            List<KnowledgeRetrievalEvidence> evidences,
            ChatMessageResult result
    ) {
        Finalization finalization = new Finalization(
                userMessage,
                assistantMessage,
                debugRunSnapshot(
                        ownership.tenantId(),
                        assistantMessage.messageId(),
                        assistantMessage.traceId(),
                        assistantMessage.createdAt(),
                        evidences
                ),
                result
        );
        chatRunTransactionService.recordResultReceived(ownership, finalization);
        try {
            chatRunTransactionService.complete(ownership, finalization);
        } catch (RuntimeException exception) {
            recordFinalizationFailureSafely(ownership, exception);
            throw new FinalizationPendingException(exception);
        }
        return result;
    }

    private void settleFailedRun(Ownership ownership, boolean modelDispatched, RuntimeException exception) {
        String failureCode = exception instanceof ApplicationException applicationException
                ? applicationException.errorCode().code()
                : AppStudioErrorCodes.CHAT_RUN_FAILED.code();
        String failureSummary = modelDispatched
                ? "模型调用已派发但结果未能持久确认（" + exception.getClass().getSimpleName() + "）"
                : "聊天运行在模型派发前失败（" + exception.getClass().getSimpleName() + "）";
        try {
            if (modelDispatched) {
                chatRunTransactionService.markDispatchedUncertain(ownership, failureCode, failureSummary);
            } else {
                chatRunTransactionService.failPreparing(ownership, failureCode, failureSummary);
            }
        } catch (RuntimeException settlementFailure) {
            exception.addSuppressed(settlementFailure);
        }
    }

    private static ApplicationException claimFailure(Claim claim) {
        return switch (claim.state()) {
            case IN_PROGRESS -> new ApplicationException(
                    AppStudioErrorCodes.CHAT_RUN_IN_PROGRESS,
                    "该 clientMessageId 正在处理中，请查询消息记录后再决定是否发起新请求"
            );
            case FAILED -> AppStudioErrorCodes.DUPLICATE_MESSAGE_REQUEST.code().equals(claim.failureCode())
                    ? new ApplicationException(AppStudioErrorCodes.DUPLICATE_MESSAGE_REQUEST, requireFailureSummary(claim))
                    : new ApplicationException(AppStudioErrorCodes.CHAT_RUN_FAILED, requireFailureSummary(claim));
            case UNCERTAIN -> new ApplicationException(
                    AppStudioErrorCodes.CHAT_RUN_UNCERTAIN,
                    requireFailureSummary(claim)
            );
            case DEAD_LETTER -> new ApplicationException(
                    AppStudioErrorCodes.CHAT_RUN_FAILED,
                    requireFailureSummary(claim)
            );
            default -> throw new IllegalStateException("当前 claim 状态不应转换为失败：" + claim.state());
        };
    }

    private void recordFinalizationFailureSafely(Ownership ownership, RuntimeException originalFailure) {
        try {
            chatRunTransactionService.recordFinalizationFailure(
                    ownership,
                    effectiveTraceId(TraceContext.currentTraceId())
            );
        } catch (RuntimeException recordingFailure) {
            originalFailure.addSuppressed(recordingFailure);
        }
    }

    private static String requireFailureSummary(Claim claim) {
        return claim.failureSummary() == null || claim.failureSummary().isBlank()
                ? "聊天运行未完成"
                : claim.failureSummary();
    }

    private static AppStudioDebugRunSnapshot debugRunSnapshot(
            TenantId tenantId,
            String assistantMessageId,
            String traceId,
            Instant createdAt,
            List<KnowledgeRetrievalEvidence> evidences
    ) {
        return new AppStudioDebugRunSnapshot(
                tenantId,
                assistantMessageId,
                traceId,
                createdAt,
                evidences.stream()
                        .map(AppStudioApplicationService::toDebugRunEvidenceSnapshot)
                        .toList()
        );
    }

    private ChatMessage newUserMessage(
            TenantId tenantId,
            String sessionId,
            String clientMessageId,
            String content,
            String traceId,
            Instant createdAt
    ) {
        return new ChatMessage(
                tenantId,
                chatSessionRepository.nextMessageId(),
                sessionId,
                ChatRole.USER,
                content,
                List.of(),
                null,
                clientMessageId,
                traceId,
                createdAt
        );
    }

    private AiApplication loadUsableApplication(TenantId tenantId, String applicationId, String actorId) {
        return loadUsableRelease(tenantId, applicationId, actorId, null).snapshot();
    }

    private AiApplicationRelease loadUsableRelease(
            TenantId tenantId,
            String applicationId,
            String actorId,
            ChatSession expectedSession
    ) {
        TenantId normalizedTenantId = requireValue(tenantId, "租户 ID");
        String normalizedActorId = requireText(actorId, "操作者 ID");
        AiApplication draft = aiApplicationRepository.findById(normalizedTenantId, applicationId)
                .orElseThrow(() -> new ApplicationException(
                        AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                        "AI 应用不可见"
                ));
        enforceTenantPermission(
                normalizedTenantId,
                normalizedActorId,
                AppStudioPermissionCatalog.APPLICATION_USE,
                "当前主体无权使用该 AI 应用"
        );
        AiApplicationRelease activeRelease = applicationReleaseRepository.findActive(
                normalizedTenantId,
                draft.applicationId()
        ).orElse(null);
        if (activeRelease == null) {
            publishSecurityAudit(
                    normalizedTenantId,
                    normalizedActorId,
                    APPLICATION_RESOURCE_TYPE,
                    draft.applicationId(),
                    "SECURITY_APP_ACCESS_DENIED",
                    "HIGH",
                    effectiveTraceId(TraceContext.currentTraceId()),
                    Map.of("reason", "active_release_missing")
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                    "AI 应用不可见"
            );
        }
        if (expectedSession != null && !matchesRelease(expectedSession, activeRelease)) {
            publishSecurityAudit(
                    normalizedTenantId,
                    normalizedActorId,
                    "APP_CHAT_SESSION",
                    expectedSession.sessionId(),
                    "SECURITY_APP_SESSION_RELEASE_CHANGED",
                    "MEDIUM",
                    effectiveTraceId(TraceContext.currentTraceId()),
                    Map.of(
                            "reason", "session_release_changed",
                            "applicationId", draft.applicationId(),
                            "pinnedReleaseId", expectedSession.releaseId(),
                            "pinnedReleaseVersion", expectedSession.releaseVersion(),
                            "activeReleaseId", activeRelease.releaseId(),
                            "activeReleaseVersion", activeRelease.version()
                    )
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.SESSION_RELEASE_CHANGED,
                    "sessionId=" + expectedSession.sessionId()
            );
        }
        if (!activeRelease.allows(normalizedActorId)) {
            publishSecurityAudit(
                    normalizedTenantId,
                    normalizedActorId,
                    APPLICATION_RESOURCE_TYPE,
                    draft.applicationId(),
                    "SECURITY_APP_ACCESS_DENIED",
                    "HIGH",
                    effectiveTraceId(TraceContext.currentTraceId()),
                    Map.of("reason", "resource_acl_denied", "requestable", true)
            );
            throw new RequestableAuthorizationException(
                    AppStudioErrorCodes.RESOURCE_ACL_REQUESTABLE,
                    "resource_acl_denied",
                    new AuthorizationRequestTarget(
                            APPLICATION_RESOURCE_TYPE,
                            draft.applicationId(),
                            AppStudioPermissionCatalog.APPLICATION_USE.value()
                    )
            );
        }
        requireKnowledgeBinding(activeRelease.snapshot());
        return activeRelease;
    }

    private static boolean matchesRelease(ChatSession session, AiApplicationRelease release) {
        return session.releaseId().equals(release.releaseId())
                && session.releaseVersion() == release.version();
    }

    private java.util.Optional<AiApplicationCatalogResult> visibleCatalogDetail(
            String actorId,
            Set<PermissionCode> permissionCodes,
            AiApplication application,
            AiApplicationRelease activeRelease
    ) {
        ApplicationAccessDecision access = decideApplicationAccess(
                actorId,
                permissionCodes,
                application
        ).orElse(null);
        if (access == null) {
            return java.util.Optional.empty();
        }
        if (access.accessMode() == ApplicationAccessMode.OWNER) {
            AiApplicationDetailResult detail = activeRelease == null
                    ? toDetailResult(application)
                    : toDetailResult(application, activeRelease);
            return java.util.Optional.of(new AiApplicationCatalogResult(
                    detail,
                    access.accessMode(),
                    access.capabilities()
            ));
        }
        if (access.accessMode() == ApplicationAccessMode.GOVERNANCE_READ_ONLY) {
            AiApplicationDetailResult detail = toGovernanceCatalogDetail(application, activeRelease);
            return java.util.Optional.of(new AiApplicationCatalogResult(
                    detail,
                    access.accessMode(),
                    access.capabilities()
            ));
        }
        if (activeRelease == null || !activeRelease.allows(actorId)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new AiApplicationCatalogResult(
                        toBusinessCatalogDetail(activeRelease),
                        access.accessMode(),
                        access.capabilities()
                ));
    }

    private java.util.Optional<ApplicationAccessDecision> decideApplicationAccess(
            String actorId,
            Set<PermissionCode> permissionCodes,
            AiApplication application
    ) {
        if (application.visiblePrincipalIds().contains(actorId)) {
            return java.util.Optional.of(new ApplicationAccessDecision(
                    ApplicationAccessMode.OWNER,
                    ApplicationCapabilitiesResult.owner(
                            permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_UPDATE),
                            permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_PUBLISH),
                            permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL)
                    )
            ));
        }
        if (permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_MANAGE_ACL)) {
            return java.util.Optional.of(new ApplicationAccessDecision(
                    ApplicationAccessMode.GOVERNANCE_READ_ONLY,
                    ApplicationCapabilitiesResult.governanceReadOnly()
            ));
        }
        if (permissionCodes.contains(AppStudioPermissionCatalog.APPLICATION_USE)) {
            return java.util.Optional.of(new ApplicationAccessDecision(
                    ApplicationAccessMode.BUSINESS_USER,
                    ApplicationCapabilitiesResult.businessUser()
            ));
        }
        return java.util.Optional.empty();
    }

    private ChatSession requireOwnedSession(TenantId tenantId, String actorId, String sessionId) {
        requireValue(tenantId, "租户 ID");
        String normalizedActorId = requireText(actorId, "操作者 ID");
        String normalizedSessionId = requireText(sessionId, "会话 ID");
        ChatSession session = chatSessionRepository.findById(tenantId, normalizedSessionId).orElse(null);
        if (session == null || !normalizedActorId.equals(session.userId())) {
            publishSecurityAudit(
                    tenantId,
                    normalizedActorId,
                    "APP_CHAT_SESSION",
                    normalizedSessionId,
                    "SECURITY_APP_SESSION_ACCESS_DENIED",
                    "HIGH",
                    effectiveTraceId(TraceContext.currentTraceId()),
                    Map.of("reason", session == null ? "resource_not_visible" : "session_owner_mismatch")
            );
            throw new ApplicationException(
                    AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                    "聊天会话不可见"
            );
        }
        return session;
    }

    private Set<PermissionCode> enforceApplicationPermission(
            TenantId tenantId,
            String actorId,
            PermissionCode requiredPermission,
            String resourceId,
            String deniedMessage
    ) {
        requireText(resourceId, "资源 ID");
        return enforceTenantPermission(tenantId, actorId, requiredPermission, deniedMessage);
    }

    private Set<PermissionCode> enforceTenantPermission(
            TenantId tenantId,
            String actorId,
            PermissionCode requiredPermission,
            String deniedMessage
    ) {
        Set<PermissionCode> permissionCodes = authorizationQueryPort.findPermissionCodes(
                tenantId,
                "user",
                requireText(actorId, "操作者 ID")
        );
        if (!permissionCodes.contains(requireValue(requiredPermission, "权限编码"))) {
            throw new ApplicationException(
                    AppStudioErrorCodes.AUTHORIZATION_DENIED,
                    requireText(deniedMessage, "拒绝消息")
            );
        }
        return permissionCodes;
    }

    private static String effectiveTraceId(String traceId) {
        String normalizedTraceId = normalizeOptionalText(traceId);
        return normalizedTraceId == null ? "appstudio-" + UUID.randomUUID() : normalizedTraceId;
    }

    private AiApplication loadApplicationRequiringPermission(
            TenantId tenantId,
            String applicationId,
            String actorId,
            PermissionCode requiredPermission,
            String deniedMessage
    ) {
        AiApplication application = requireApplication(tenantId, applicationId);
        enforceApplicationPermission(
                tenantId,
                actorId,
                requiredPermission,
                application.applicationId(),
                deniedMessage
        );
        if (!application.visiblePrincipalIds().contains(actorId)) {
            throw new ApplicationException(
                    AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                    "AI 应用不可见"
            );
        }
        return application;
    }

    private ApplicationGovernanceAccess loadApplicationForGovernanceRead(
            TenantId tenantId,
            String applicationId,
            String actorId
    ) {
        AiApplication application = requireApplication(tenantId, applicationId);
        Set<PermissionCode> permissionCodes = enforceApplicationPermission(
                tenantId,
                actorId,
                AppStudioPermissionCatalog.APPLICATION_READ,
                application.applicationId(),
                "当前主体无权查看应用发布版本"
        );
        ApplicationAccessDecision access = decideApplicationAccess(actorId, permissionCodes, application)
                .orElse(null);
        if (access != null && access.capabilities().canReadReleaseEvidence()) {
            return new ApplicationGovernanceAccess(application, access.accessMode());
        }
        throw new ApplicationException(
                AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                "AI 应用不可见"
        );
    }

    private AiApplication requireApplication(TenantId tenantId, String applicationId) {
        return aiApplicationRepository.findById(tenantId, applicationId)
                .orElseThrow(() -> new ApplicationException(
                        AppStudioErrorCodes.RESOURCE_NOT_VISIBLE,
                        "AI 应用不可见"
                ));
    }

    private static Citation toDomainCitation(KnowledgeRetrievalCitation citation) {
        return new Citation(
                citation.knowledgeBaseId(),
                citation.documentId(),
                citation.chunkId(),
                citation.documentName(),
                citation.fragment(),
                citation.location(),
                citation.score()
        );
    }

    private static AppStudioDebugRunEvidenceSnapshot toDebugRunEvidenceSnapshot(KnowledgeRetrievalEvidence evidence) {
        return new AppStudioDebugRunEvidenceSnapshot(
                evidence.knowledgeBaseId(),
                evidence.documentId(),
                evidence.chunkId(),
                evidence.score(),
                evidence.retrievalSignal(),
                evidence.indexVersion()
        );
    }

    private static String finalizeGroundedAnswer(String rawAnswer, List<Citation> citations) {
        String normalizedAnswer = requireText(rawAnswer, "模型回答");
        List<String> fragments = citationFragments(citations);
        if (fragments.isEmpty()) {
            return normalizedAnswer;
        }
        if (containsUnsupportedNumericClaim(normalizedAnswer, fragments) || looksLikeUngroundedNoAnswer(normalizedAnswer)) {
            return answerFromFragments(fragments);
        }
        return normalizedAnswer;
    }

    private static List<String> citationFragments(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        return citations.stream()
                .map(Citation::fragment)
                .map(AppStudioApplicationService::normalizeOptionalText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static String answerFromFragments(List<String> fragments) {
        String fragmentBody = java.util.stream.IntStream.range(0, fragments.size())
                .mapToObj(index -> (index + 1) + ". " + fragments.get(index))
                .collect(Collectors.joining("\n"));
        return "根据引用原文，制度内容如下：\n" + fragmentBody;
    }

    private static boolean containsUnsupportedNumericClaim(String answer, List<String> fragments) {
        String evidenceText = compactNumericClaimText(String.join("\n", fragments));
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\d+(?:\\.\\d+)?\\s*(?:元|个工作日|天|小时|分钟|%|％)")
                .matcher(answer);
        while (matcher.find()) {
            if (!evidenceText.contains(compactNumericClaimText(matcher.group()))) {
                return true;
            }
        }
        return false;
    }

    private static String compactNumericClaimText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private static boolean looksLikeUngroundedNoAnswer(String answer) {
        return answer.contains("未找到")
                || answer.contains("无法确定")
                || answer.contains("没有提供")
                || answer.contains("证据不足")
                || answer.contains("无法回答");
    }

    private void publishAppAudit(
            TenantId tenantId,
            String actorId,
            String resourceId,
            String action,
            String riskLevel,
            String traceId,
            Map<String, Object> details
    ) {
        publishSecurityAudit(
                tenantId,
                actorId,
                APPLICATION_RESOURCE_TYPE,
                resourceId,
                action,
                riskLevel,
                traceId,
                details
        );
    }

    private String publishSecurityAudit(
            TenantId tenantId,
            String actorId,
            String resourceType,
            String resourceId,
            String action,
            String riskLevel,
            String traceId,
            Map<String, Object> details
    ) {
        String eventId = UUID.randomUUID().toString();
        auditPublisher.publish(new AuditEvent(
                eventId,
                tenantId.value(),
                actorId,
                resourceType,
                resourceId,
                action,
                riskLevel,
                null,
                traceId,
                Instant.now(clock),
                details
        ));
        return eventId;
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static List<String> deduplicate(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String value : values) {
            ordered.add(requireText(value, fieldName));
        }
        return List.copyOf(ordered);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeSessionTitle(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? "未命名会话" : normalized;
    }

    private Set<String> releaseAudience(TenantId tenantId, AiApplication application) {
        Set<String> audience = new LinkedHashSet<>(application.visiblePrincipalIds());
        audience.addAll(resourceAudienceQueryPort.findAllowedUserIds(
                tenantId,
                APPLICATION_RESOURCE_TYPE,
                application.applicationId(),
                AppStudioPermissionCatalog.APPLICATION_USE
        ));
        return Set.copyOf(audience);
    }

    private static String publishCandidateFingerprint(
            AiApplication application,
            Set<String> releaseAudience
    ) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintValue(canonical, "app-publish-candidate-v1");
        appendFingerprintValue(canonical, application.tenantId().value());
        appendFingerprintValue(canonical, application.applicationId());
        appendFingerprintValue(canonical, application.type().name());
        appendFingerprintValue(canonical, application.name());
        appendFingerprintValue(canonical, application.description());
        appendFingerprintValue(canonical, application.modelConfigId());
        appendSortedFingerprintValues(canonical, application.knowledgeBaseIds());
        appendSortedFingerprintValues(canonical, application.toolIds());
        appendSortedFingerprintValues(canonical, application.visiblePrincipalIds());
        appendFingerprintValue(canonical, application.systemPrompt());
        appendFingerprintValue(canonical, application.publicationStatus().name());
        appendFingerprintValue(canonical, application.createdAt().toString());
        appendSortedFingerprintValues(canonical, releaseAudience);
        return sha256(canonical.toString());
    }

    private static void appendSortedFingerprintValues(
            StringBuilder canonical,
            Collection<String> values
    ) {
        List<String> sorted = values.stream().sorted().toList();
        appendFingerprintValue(canonical, String.valueOf(sorted.size()));
        sorted.forEach(value -> appendFingerprintValue(canonical, value));
    }

    private static void appendFingerprintValue(StringBuilder canonical, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        canonical.append(encoded.length).append(':').append(value);
    }

    private static boolean matchesPublishCandidate(String stored, String current) {
        return !INVALIDATED_LEGACY_PREFLIGHT_FINGERPRINT.equals(stored)
                && Objects.equals(stored, current);
    }

    private ApplicationReleaseRepository.MutationResult createAndActivateRelease(
            ApplicationReleaseRepository.MutationCommand command
    ) {
        try {
            return applicationReleaseRepository.createAndActivate(command);
        } catch (ApplicationReleaseConflictException exception) {
            throw new ApplicationException(AppStudioErrorCodes.PUBLISH_BLOCKED, exception.getMessage());
        }
    }

    private void acquireApplicationMutationLock(
            TenantId tenantId,
            String applicationId,
            ErrorCode conflictCode
    ) {
        try {
            aiApplicationRepository.acquireMutationLock(tenantId, applicationId);
        } catch (ApplicationReleaseConflictException exception) {
            throw new ApplicationException(conflictCode, exception.getMessage());
        }
    }

    private Optional<AiApplicationDetailResult> findCommittedPublishReplay(
            TenantId tenantId,
            String applicationId,
            String publishRequestId
    ) {
        return applicationReleaseRepository.findByRequestId(tenantId, applicationId, publishRequestId)
                .map(release -> {
                    requireIdempotentReleaseMatches(release, ApplicationReleaseOperation.PUBLISH, null);
                    return toDetailResult(release);
                });
    }

    private static void requireIdempotentReleaseMatches(
            AiApplicationRelease existing,
            ApplicationReleaseOperation expectedOperation,
            String expectedSourceReleaseId
    ) {
        if (existing.operation() != expectedOperation
                || !Objects.equals(existing.sourceReleaseId(), expectedSourceReleaseId)) {
            throw new ApplicationException(
                    AppStudioErrorCodes.PUBLISH_BLOCKED,
                    "幂等请求 ID 已用于不同的发布操作"
            );
        }
    }

    private static AiApplication publishedSnapshot(AiApplication application, Instant publishedAt) {
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
                publishedAt
        );
    }

    private static void requireKnowledgeBinding(AiApplication application) {
        if (application.knowledgeBaseIds().isEmpty()) {
            throw new IllegalStateException("AI 应用未绑定知识库，无法执行知识问答");
        }
    }

    private AiApplicationDetailResult toGovernanceCatalogDetail(
            AiApplication application,
            AiApplicationRelease activeRelease
    ) {
        if (activeRelease == null) {
            return new AiApplicationDetailResult(
                    application.applicationId(),
                    "APP-" + application.applicationId(),
                    "未发布应用 APP-" + application.applicationId(),
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    "UNPUBLISHED",
                    application.createdAt(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
        AiApplication snapshot = activeRelease.snapshot();
        return new AiApplicationDetailResult(
                application.applicationId(),
                "APP-" + application.applicationId(),
                snapshot.name(),
                null,
                snapshot.type().name(),
                null,
                List.of(),
                null,
                "PUBLISHED",
                application.createdAt(),
                null,
                activeRelease.releaseId(),
                activeRelease.version(),
                activeRelease.operation().name(),
                activeRelease.sourceReleaseId(),
                findSourceReleaseVersion(activeRelease)
        );
    }

    private AiApplicationDetailResult toBusinessCatalogDetail(AiApplicationRelease activeRelease) {
        AiApplication snapshot = activeRelease.snapshot();
        return new AiApplicationDetailResult(
                snapshot.applicationId(),
                "APP-" + snapshot.applicationId(),
                snapshot.name(),
                snapshot.description(),
                snapshot.type().name(),
                null,
                List.of(),
                null,
                "PUBLISHED",
                snapshot.createdAt(),
                activeRelease.publishedAt(),
                activeRelease.releaseId(),
                activeRelease.version(),
                activeRelease.operation().name(),
                activeRelease.sourceReleaseId(),
                findSourceReleaseVersion(activeRelease)
        );
    }

    private static AiApplicationDetailResult toDetailResult(AiApplication application) {
        return new AiApplicationDetailResult(
                application.applicationId(),
                "APP-" + application.applicationId(),
                application.name(),
                application.description(),
                application.type().name(),
                application.modelConfigId(),
                application.knowledgeBaseIds(),
                application.systemPrompt(),
                application.publicationStatus().name(),
                application.createdAt(),
                application.updatedAt(),
                null,
                null,
                null,
                null,
                null
        );
    }

    private AiApplicationDetailResult toDetailResult(AiApplicationRelease release) {
        return toDetailResult(release.snapshot(), release);
    }

    private AiApplicationDetailResult toDetailResult(
            AiApplication application,
            AiApplicationRelease activeRelease
    ) {
        return new AiApplicationDetailResult(
                application.applicationId(),
                "APP-" + application.applicationId(),
                application.name(),
                application.description(),
                application.type().name(),
                application.modelConfigId(),
                application.knowledgeBaseIds(),
                application.systemPrompt(),
                application.publicationStatus().name(),
                application.createdAt(),
                application.updatedAt(),
                activeRelease.releaseId(),
                activeRelease.version(),
                activeRelease.operation().name(),
                activeRelease.sourceReleaseId(),
                findSourceReleaseVersion(activeRelease)
        );
    }

    private Long findSourceReleaseVersion(AiApplicationRelease release) {
        return release.sourceReleaseId() == null
                ? null
                : applicationReleaseRepository.findById(
                                release.tenantId(),
                                release.applicationId(),
                                release.sourceReleaseId()
                        )
                        .map(AiApplicationRelease::version)
                        .orElseThrow(() -> new IllegalStateException("应用发布版本来源不存在"));
    }

    private static String systemPromptSummary(String prompt) {
        String normalized = prompt.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }

    private static Long sourceReleaseVersion(
            AiApplicationRelease release,
            Map<String, Long> versionByReleaseId
    ) {
        if (release.sourceReleaseId() == null) {
            return null;
        }
        Long sourceVersion = versionByReleaseId.get(release.sourceReleaseId());
        if (sourceVersion == null) {
            throw new IllegalStateException("应用发布版本来源不存在");
        }
        return sourceVersion;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
