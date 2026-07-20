package com.aiplatform.knowledge.application;

import com.aiplatform.knowledge.application.command.CreateKnowledgeBaseCommand;
import com.aiplatform.knowledge.application.command.DisableKnowledgeBaseCommand;
import com.aiplatform.knowledge.application.command.UpdateKnowledgeBaseCommand;
import com.aiplatform.knowledge.application.result.CreateKnowledgeBaseResult;
import com.aiplatform.knowledge.application.result.KnowledgeBaseDetailResult;
import com.aiplatform.knowledge.application.result.KnowledgeBaseListItemResult;
import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.KnowledgeVisibility;
import com.aiplatform.knowledge.domain.port.KnowledgeBaseRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.ErrorCode;
import com.aiplatform.shared.observability.TraceContext;
import com.aiplatform.shared.security.port.RbacAuthorizationQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库应用服务，负责编排租户内知识库创建。
 */
@Service
public class KnowledgeBaseApplicationService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final ErrorCode EMBEDDING_MODEL_DENIED = ErrorCode.of("AIP-KB-EMBEDDINGDENIED", "向量模型不可用于当前知识库");
    private static final ErrorCode KNOWLEDGE_BASE_NOT_FOUND = ErrorCode.of("AIP-KB-NOTFOUND", "知识库不存在");
    private static final ErrorCode KNOWLEDGE_BASE_DISABLED = ErrorCode.of("AIP-KB-DISABLED", "知识库已停用");

    private final KnowledgeBaseRepository repository;
    private final ModelUsagePolicyPort modelUsagePolicyPort;
    private final RbacAuthorizationQueryPort authorizationQueryPort;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;

    public KnowledgeBaseApplicationService(
            KnowledgeBaseRepository repository,
            ModelUsagePolicyPort modelUsagePolicyPort,
            RbacAuthorizationQueryPort authorizationQueryPort,
            AuditEventPublisher auditPublisher,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "知识库仓储不能为空");
        this.modelUsagePolicyPort = Objects.requireNonNull(modelUsagePolicyPort, "模型使用策略端口不能为空");
        this.authorizationQueryPort = Objects.requireNonNull(authorizationQueryPort, "权限快照查询端口不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布器不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Transactional
    public CreateKnowledgeBaseResult createKnowledgeBase(CreateKnowledgeBaseCommand command) {
        Objects.requireNonNull(command, "创建知识库命令不能为空");
        Instant now = Instant.now(clock);
        KnowledgeVisibility visibility = toVisibility(command.visibility());
        String knowledgeBaseId = repository.nextKnowledgeBaseId();
        ensureEmbeddingModelAllowed(command, knowledgeBaseId);
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                command.tenantId(),
                knowledgeBaseId,
                command.name(),
                command.description(),
                visibility,
                Set.of(command.operatorId()),
                command.operatorId(),
                command.embeddingModelConfigId(),
                true,
                true,
                now,
                now
        );
        KnowledgeBase saved = repository.save(knowledgeBase);
        return new CreateKnowledgeBaseResult(saved.knowledgeBaseId(), STATUS_ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseListItemResult> listKnowledgeBases(com.aiplatform.shared.kernel.TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        return repository.findListItemsByTenant(tenantId).stream()
                .map(KnowledgeBaseListItemResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseDetailResult getKnowledgeBase(com.aiplatform.shared.kernel.TenantId tenantId, String knowledgeBaseId) {
        return KnowledgeBaseDetailResult.from(requireKnowledgeBase(tenantId, knowledgeBaseId));
    }

    @Transactional
    public KnowledgeBaseDetailResult updateKnowledgeBase(UpdateKnowledgeBaseCommand command) {
        Objects.requireNonNull(command, "更新知识库命令不能为空");
        KnowledgeBase existing = requireKnowledgeBase(command.tenantId(), command.knowledgeBaseId());
        if (!existing.published()) {
            throw new ApplicationException(KNOWLEDGE_BASE_DISABLED, "知识库ID=" + command.knowledgeBaseId());
        }
        Instant now = Instant.now(clock);
        if (!existing.embeddingModelConfigId().equals(command.embeddingModelConfigId())) {
            ensureEmbeddingModelAllowed(command, existing.knowledgeBaseId());
        }
        KnowledgeBase saved = repository.save(existing.withSettings(
                command.name(),
                command.description(),
                toVisibility(command.visibility()),
                command.embeddingModelConfigId(),
                now
        ));
        audit(command.tenantId().value(), command.operatorId(), saved.knowledgeBaseId(),
                "KNOWLEDGE_BASE_UPDATE", "MEDIUM", now,
                Map.of("knowledgeBaseName", saved.name(), "visibility", command.visibility()));
        return KnowledgeBaseDetailResult.from(saved);
    }

    @Transactional
    public KnowledgeBaseDetailResult disableKnowledgeBase(DisableKnowledgeBaseCommand command) {
        Objects.requireNonNull(command, "停用知识库命令不能为空");
        KnowledgeBase existing = requireKnowledgeBase(command.tenantId(), command.knowledgeBaseId());
        Instant now = Instant.now(clock);
        KnowledgeBase saved = repository.save(existing.disable(now));
        audit(command.tenantId().value(), command.operatorId(), saved.knowledgeBaseId(),
                "KNOWLEDGE_BASE_DISABLE", "HIGH", now,
                Map.of("knowledgeBaseName", saved.name()));
        return KnowledgeBaseDetailResult.from(saved);
    }

    private void ensureEmbeddingModelAllowed(CreateKnowledgeBaseCommand command, String knowledgeBaseId) {
        KnowledgeModelUsageDecision decision = modelUsagePolicyPort.decide(new KnowledgeModelUsageRequest(
                command.tenantId(),
                command.operatorId(),
                command.operatorType(),
                authorizationQueryPort.findRoleCodes(command.tenantId(), command.operatorType(), command.operatorId()),
                authorizationQueryPort.findPermissionCodes(command.tenantId(), command.operatorType(), command.operatorId()),
                knowledgeBaseId,
                KnowledgeModelUseCase.RAG_INDEXING,
                KnowledgeModelCapability.EMBEDDING,
                command.embeddingModelConfigId(),
                Map.of("reason", "create-knowledge-base"),
                1,
                0
        ));
        if (!decision.allowed()) {
            throw new ApplicationException(EMBEDDING_MODEL_DENIED, decision.reason());
        }
    }

    private void ensureEmbeddingModelAllowed(UpdateKnowledgeBaseCommand command, String knowledgeBaseId) {
        KnowledgeModelUsageDecision decision = modelUsagePolicyPort.decide(new KnowledgeModelUsageRequest(
                command.tenantId(),
                command.operatorId(),
                command.operatorType(),
                authorizationQueryPort.findRoleCodes(command.tenantId(), command.operatorType(), command.operatorId()),
                authorizationQueryPort.findPermissionCodes(command.tenantId(), command.operatorType(), command.operatorId()),
                knowledgeBaseId,
                KnowledgeModelUseCase.RAG_INDEXING,
                KnowledgeModelCapability.EMBEDDING,
                command.embeddingModelConfigId(),
                Map.of("reason", "update-knowledge-base"),
                1,
                0
        ));
        if (!decision.allowed()) {
            throw new ApplicationException(EMBEDDING_MODEL_DENIED, decision.reason());
        }
    }

    private KnowledgeBase requireKnowledgeBase(com.aiplatform.shared.kernel.TenantId tenantId, String knowledgeBaseId) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        return repository.findById(tenantId, knowledgeBaseId)
                .orElseThrow(() -> new ApplicationException(KNOWLEDGE_BASE_NOT_FOUND, "知识库ID=" + knowledgeBaseId));
    }

    private void audit(
            String tenantId,
            String operatorId,
            String knowledgeBaseId,
            String action,
            String riskLevel,
            Instant now,
            Map<String, Object> details
    ) {
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(),
                tenantId,
                operatorId,
                "KNOWLEDGE_BASE",
                knowledgeBaseId,
                action,
                riskLevel,
                null,
                TraceContext.currentTraceId(),
                now,
                details
        ));
    }

    private static KnowledgeVisibility toVisibility(String visibility) {
        return switch (visibility.toLowerCase(Locale.ROOT)) {
            case "public" -> KnowledgeVisibility.PUBLIC;
            case "department" -> KnowledgeVisibility.DEPARTMENT;
            case "project" -> KnowledgeVisibility.PROJECT;
            case "private" -> KnowledgeVisibility.PRIVATE;
            case "members" -> KnowledgeVisibility.SPECIFIED_MEMBERS;
            case "roles" -> KnowledgeVisibility.SPECIFIED_ROLES;
            default -> throw new IllegalArgumentException("不支持的知识库可见性：" + visibility);
        };
    }
}
