package com.aiplatform.appstudio.domain.modelpolicy.spec;

import com.aiplatform.appstudio.domain.modelpolicy.AppModelUsagePolicyProjection;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageDecision;
import com.aiplatform.appstudio.domain.port.modelpolicy.model.AppModelUsageRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * AI 应用模型使用策略规则，基于本地投影完成模型授权、能力和额度判定。
 */
public class AppModelUsagePolicySpec {
    private final Clock clock;

    public AppModelUsagePolicySpec(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public AppModelUsageDecision decide(AppModelUsagePolicyProjection projection, AppModelUsageRequest request) {
        Objects.requireNonNull(projection, "模型策略投影不能为空");
        Objects.requireNonNull(request, "模型使用请求不能为空");
        Instant now = Instant.now(clock);
        if (!projection.enabled()) {
            return deny("model_policy_disabled", "模型使用策略已停用");
        }
        if (projection.expiresAt() != null && !projection.expiresAt().isAfter(now)) {
            return deny("model_policy_expired", "模型使用策略已过期");
        }
        if (!projection.allowedUseCases().contains(normalize(request.useCase()))) {
            return deny("use_case_not_allowed", "模型使用场景未授权");
        }
        if (!projection.capabilities().contains(normalize(request.requiredCapability()))) {
            return deny("capability_not_allowed", "模型能力未授权");
        }
        if (!projection.allowedApplicationIds().isEmpty()
                && !projection.allowedApplicationIds().contains(request.applicationId())) {
            return deny("application_not_allowed", "业务应用未授权使用该模型");
        }
        if (projection.maxInputTokens() > 0 && request.estimatedInputTokens() > projection.maxInputTokens()) {
            return deny("input_tokens_exceeded", "输入 Token 超过策略上限");
        }
        if (projection.maxOutputTokens() > 0 && request.estimatedOutputTokens() > projection.maxOutputTokens()) {
            return deny("output_tokens_exceeded", "输出 Token 超过策略上限");
        }
        return new AppModelUsageDecision(
                true,
                "allowed",
                "模型使用策略允许当前请求",
                new AppModelRuntimeDescriptor(
                        projection.modelConfigId(),
                        projection.providerId(),
                        projection.providerCode(),
                        projection.providerKind(),
                        projection.connectionRef(),
                        projection.modelCode(),
                        projection.modelConfigVersion(),
                        projection.modelType(),
                        projection.capabilities(),
                        projection.effectiveOptions(),
                        projection.effectiveOptionsDigest(),
                        projection.policyVersion()
                ),
                Map.of(
                        "policyVersion", projection.policyVersion(),
                        "providerCode", projection.providerCode(),
                        "modelConfigId", projection.modelConfigId()
                )
        );
    }

    private static AppModelUsageDecision deny(String decisionCode, String reason) {
        return new AppModelUsageDecision(false, decisionCode, reason, null, Map.of());
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
