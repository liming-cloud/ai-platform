package com.aiplatform.knowledge.domain.modelpolicy.spec;

import com.aiplatform.knowledge.domain.modelpolicy.KnowledgeModelUsagePolicyProjection;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageDecision;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUsageRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 知识库模型使用策略规格，集中校验启用状态、能力、场景、资源范围和 options 上限。
 */
public class KnowledgeModelUsagePolicySpec {
    private final Clock clock;

    public KnowledgeModelUsagePolicySpec(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    public KnowledgeModelUsageDecision decide(
            KnowledgeModelUsagePolicyProjection projection,
            KnowledgeModelUsageRequest request
    ) {
        Objects.requireNonNull(projection, "模型策略投影不能为空");
        Objects.requireNonNull(request, "模型使用请求不能为空");
        if (!projection.enabled()) {
            return KnowledgeModelUsageDecision.deny("model_policy_disabled", "模型使用策略未启用");
        }
        if (projection.expiresAt() != null && !projection.expiresAt().isAfter(Instant.now(clock))) {
            return KnowledgeModelUsageDecision.deny("model_policy_expired", "模型使用策略已过期");
        }
        if (!projection.allowedUseCases().contains(request.useCase())) {
            return KnowledgeModelUsageDecision.deny("use_case_not_allowed", "模型不允许用于当前知识库场景");
        }
        if (!projection.capabilities().contains(request.requiredCapability())) {
            return KnowledgeModelUsageDecision.deny("capability_not_allowed", "模型能力不满足当前知识库场景");
        }
        if (!projection.allowsKnowledgeBase(request.knowledgeBaseId())) {
            return KnowledgeModelUsageDecision.deny("knowledge_base_not_allowed", "模型未授权给当前知识库");
        }
        if (projection.maxInputTokens() > 0 && request.estimatedInputTokens() > projection.maxInputTokens()) {
            return KnowledgeModelUsageDecision.deny("input_tokens_exceeded", "请求输入 Token 超出模型策略上限");
        }
        if (projection.maxOutputTokens() > 0 && request.estimatedOutputTokens() > projection.maxOutputTokens()) {
            return KnowledgeModelUsageDecision.deny("output_tokens_exceeded", "请求输出 Token 超出模型策略上限");
        }
        return KnowledgeModelUsageDecision.allow(projection.runtimeDescriptor(), Map.of(
                "modelConfigId", projection.modelConfigId(),
                "modelCode", projection.modelCode(),
                "providerCode", projection.providerCode(),
                "policyVersion", projection.policyVersion()
        ));
    }
}
