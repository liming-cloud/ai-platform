package com.aiplatform.modelhub.infrastructure.messaging.inbox;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.modelhub.application.ModelApprovalDecisionApplicationService;
import com.aiplatform.modelhub.infrastructure.messaging.model.ApprovalDecisionPayload;
import com.aiplatform.modelhub.infrastructure.messaging.model.LegacyApprovalDecisionPayload;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ApprovalDecisionInboxMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在一个本地事务中领取事件 ID 并应用审批结果，确保重复投递不重复产生状态与 outbox 副作用。
 */
@Service
public class ApprovalDecisionInboxService {
    private static final String SOURCE_RESOURCE_TYPE_PROVIDER = "model_provider";
    private static final String SOURCE_RESOURCE_TYPE_CONFIG = "model_config";

    private final ApprovalDecisionInboxMapper mapper;
    private final ModelApprovalDecisionApplicationService decisions;

    public ApprovalDecisionInboxService(
            ApprovalDecisionInboxMapper mapper,
            ModelApprovalDecisionApplicationService decisions
    ) {
        this.mapper = Objects.requireNonNull(mapper, "审批决策 inbox Mapper 不能为空");
        this.decisions = Objects.requireNonNull(decisions, "模型审批决策服务不能为空");
    }

    @Transactional
    public boolean process(DomainEventMessage message, ApprovalDecisionPayload payload) {
        Objects.requireNonNull(message, "审批决策事件不能为空");
        Objects.requireNonNull(payload, "审批决策载荷不能为空");
        if (!isSupportedResource(payload.sourceResourceType())) {
            return false;
        }
        if (!claim(message, payload)) {
            return false;
        }
        TenantId tenantId = TenantId.of(message.tenantId());
        switch (payload.sourceResourceType()) {
            case SOURCE_RESOURCE_TYPE_PROVIDER -> decisions.applyProviderDecision(
                    tenantId,
                    payload.sourceResourceId(),
                    payload.approvalRequestId(),
                    payload.evidenceSchema(),
                    payload.evidenceDigest(),
                    payload.targetDigest(),
                    payload.isApproved(),
                    message.occurredAt()
            );
            case SOURCE_RESOURCE_TYPE_CONFIG -> decisions.applyModelConfigDecision(
                    tenantId,
                    payload.sourceResourceId(),
                    payload.approvalRequestId(),
                    payload.evidenceSchema(),
                    payload.evidenceDigest(),
                    payload.targetDigest(),
                    payload.isApproved(),
                    message.occurredAt()
            );
            default -> throw new IllegalStateException("已校验的审批资源类型发生变化");
        }
        return true;
    }

    /**
     * v2 carried request correlation but no immutable evidence. Migrations retire its pending resources,
     * so delayed delivery is receipted for idempotency and must never mutate model state.
     */
    @Transactional
    public boolean processLegacyCorrelated(DomainEventMessage message, ApprovalDecisionPayload payload) {
        Objects.requireNonNull(message, "审批决策事件不能为空");
        Objects.requireNonNull(payload, "旧版审批决策载荷不能为空");
        return isSupportedResource(payload.sourceResourceType()) && claim(message, payload);
    }

    @Transactional
    public boolean processLegacy(DomainEventMessage message, LegacyApprovalDecisionPayload legacyPayload) {
        Objects.requireNonNull(message, "审批决策事件不能为空");
        Objects.requireNonNull(legacyPayload, "旧版审批决策载荷不能为空");
        if (!isSupportedResource(legacyPayload.sourceResourceType())) {
            return false;
        }
        long tenantId = com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong(
                message.tenantId(),
                "租户 ID"
        );
        long taskId = com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong(
                legacyPayload.taskId(),
                "审批任务 ID"
        );
        long resourceId = com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong(
                legacyPayload.sourceResourceId(),
                "来源资源 ID"
        );
        String approvalRequestId = mapper.resolveLegacyApprovalRequestId(
                tenantId,
                taskId,
                legacyPayload.sourceContext(),
                legacyPayload.sourceResourceType(),
                resourceId
        );
        if (approvalRequestId == null || approvalRequestId.isBlank()) {
            return false;
        }
        return processLegacyCorrelated(message, legacyPayload.withApprovalRequestId(approvalRequestId));
    }

    private boolean claim(DomainEventMessage message, ApprovalDecisionPayload payload) {
        int inserted = mapper.insertReceipt(ApprovalDecisionInboxReceipt.from(message, payload));
        if (inserted == 0) {
            return false;
        }
        if (inserted != 1) {
            throw new IllegalStateException("审批决策 inbox 写入结果不合法");
        }
        return true;
    }

    private static boolean isSupportedResource(String resourceType) {
        return SOURCE_RESOURCE_TYPE_PROVIDER.equals(resourceType)
                || SOURCE_RESOURCE_TYPE_CONFIG.equals(resourceType);
    }
}
