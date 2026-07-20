package com.aiplatform.appstudio.infrastructure.messaging.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 模型使用策略发布事件载荷，app-studio 将其消费为本地模型投影。
 */
public record ModelUsagePolicyPublishedPayload(
        String modelConfigId,
        String providerId,
        String providerCode,
        String providerKind,
        String providerEndpoint,
        String authType,
        String secretRefId,
        String secretMaskedValue,
        String modelCode,
        Integer modelConfigVersion,
        String modelType,
        List<String> capabilities,
        List<String> allowedUseCases,
        List<String> allowedKnowledgeBaseIds,
        List<String> allowedApplicationIds,
        Map<String, Object> effectiveOptions,
        String effectiveOptionsDigest,
        Integer maxInputTokens,
        Integer maxOutputTokens,
        String policyVersion,
        Long policyRevision,
        String policyDigest,
        Instant expiresAt,
        Boolean enabled
) {
}
