package com.aiplatform.appstudio.infrastructure.persistence.repository;

import com.aiplatform.appstudio.domain.modelpolicy.spec.AppModelUsagePolicySpec;
import com.aiplatform.appstudio.domain.port.modelpolicy.AppModelUsagePolicyProjectionRepository;
import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyPort;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 基于本地投影的 AI 应用模型使用策略适配器，投影缺失时默认拒绝。
 */
@Component
public class DatabaseAppModelUsagePolicyAdapter implements ModelUsagePolicyPort {
    private final AppModelUsagePolicyProjectionRepository projections;
    private final AppModelUsagePolicySpec spec;

    public DatabaseAppModelUsagePolicyAdapter(
            AppModelUsagePolicyProjectionRepository projections,
            Clock clock
    ) {
        this.projections = Objects.requireNonNull(projections, "模型策略投影仓储不能为空");
        this.spec = new AppModelUsagePolicySpec(clock);
    }

    @Override
    public AppModelUsageDecision decide(AppModelUsageRequest request) {
        Objects.requireNonNull(request, "模型使用请求不能为空");
        return projections.findByModelConfigId(request.tenantId(), request.requestedModelConfigId())
                .map(projection -> spec.decide(projection, request))
                .orElseGet(() -> new AppModelUsageDecision(
                        false,
                        "model_policy_projection_missing",
                        "模型使用策略投影不存在或未同步",
                        null,
                        Map.of()
                ));
    }
}
