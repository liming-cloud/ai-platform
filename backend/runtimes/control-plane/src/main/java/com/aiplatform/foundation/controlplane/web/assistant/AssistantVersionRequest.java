package com.aiplatform.foundation.controlplane.web.assistant;

import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
import java.util.UUID;

/**
 * 创建助手不可变版本草稿的公共请求。
 */
public record AssistantVersionRequest(
        UUID chatDeploymentId,
        String knowledgeSpaceId,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) {
}
