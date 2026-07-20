package com.aiplatform.modelhub.domain.modelpolicy.spec;

import com.aiplatform.modelhub.domain.modelpolicy.ModelUsagePolicy;
import com.aiplatform.modelhub.domain.modelpolicy.model.ModelUsagePolicyDecision;
import com.aiplatform.modelhub.domain.modelpolicy.model.ModelUsagePolicyRequest;
import java.time.Instant;
import java.util.Objects;

/**
 * 模型使用策略领域规则，统一执行模型停用、能力、用例、应用和 Token 上限校验。
 */
public final class ModelUsagePolicySpec {
    public ModelUsagePolicyDecision decide(ModelUsagePolicy policy, ModelUsagePolicyRequest request, Instant now) {
        Objects.requireNonNull(policy, "模型使用策略不能为空");
        Objects.requireNonNull(request, "模型使用策略请求不能为空");
        Objects.requireNonNull(now, "校验时间不能为空");
        if (!policy.enabled()) {
            return ModelUsagePolicyDecision.deny("model_policy_disabled", "模型使用策略已停用", policy.policyVersion());
        }
        if (policy.expiredAt(now)) {
            return ModelUsagePolicyDecision.deny("model_policy_expired", "模型使用策略已过期", policy.policyVersion());
        }
        if (!policy.allowedUseCases().contains(request.useCase())) {
            return ModelUsagePolicyDecision.deny("use_case_not_allowed", "模型使用场景未授权", policy.policyVersion());
        }
        if (!policy.allowedCapabilities().contains(request.requiredCapability())) {
            return ModelUsagePolicyDecision.deny("capability_not_allowed", "模型能力未授权", policy.policyVersion());
        }
        if (!policy.allowedApplicationIds().isEmpty()
                && !policy.allowedApplicationIds().contains(request.businessResourceId())) {
            return ModelUsagePolicyDecision.deny("application_not_allowed", "业务应用未授权使用该模型", policy.policyVersion());
        }
        if (policy.maxInputTokens() > 0 && request.estimatedInputTokens() > policy.maxInputTokens()) {
            return ModelUsagePolicyDecision.deny("input_tokens_exceeded", "输入 Token 超过策略上限", policy.policyVersion());
        }
        if (policy.maxOutputTokens() > 0 && request.estimatedOutputTokens() > policy.maxOutputTokens()) {
            return ModelUsagePolicyDecision.deny("output_tokens_exceeded", "输出 Token 超过策略上限", policy.policyVersion());
        }
        return ModelUsagePolicyDecision.allow(policy.policyVersion());
    }
}
