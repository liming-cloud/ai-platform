package com.aiplatform.knowledge.infrastructure.persistence.repository;

import com.aiplatform.knowledge.domain.modelpolicy.spec.KnowledgeModelUsagePolicySpec;
import com.aiplatform.knowledge.domain.port.modelpolicy.KnowledgeModelUsagePolicyProjectionRepository;
import com.aiplatform.knowledge.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 基于本地投影的知识库模型使用策略适配器，投影缺失时默认拒绝。
 */
@Component
public class DatabaseKnowledgeModelUsagePolicyAdapter implements ModelUsagePolicyPort {
    private final KnowledgeModelUsagePolicyProjectionRepository projections;
    private final KnowledgeModelUsagePolicySpec spec;

    public DatabaseKnowledgeModelUsagePolicyAdapter(
            KnowledgeModelUsagePolicyProjectionRepository projections,
            Clock clock
    ) {
        this.projections = Objects.requireNonNull(projections, "模型策略投影仓储不能为空");
        this.spec = new KnowledgeModelUsagePolicySpec(clock);
    }

    @Override
    public KnowledgeModelUsageDecision decide(KnowledgeModelUsageRequest request) {
        Objects.requireNonNull(request, "模型使用请求不能为空");
        return projections.findByModelConfigId(request.tenantId(), request.requestedModelConfigId())
                .map(projection -> spec.decide(projection, request))
                .orElseGet(() -> KnowledgeModelUsageDecision.deny(
                        "model_policy_projection_missing",
                        "模型使用策略投影不存在或未同步"
                ));
    }
}
