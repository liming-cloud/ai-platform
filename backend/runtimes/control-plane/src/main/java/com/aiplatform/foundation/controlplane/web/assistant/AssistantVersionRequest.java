package com.aiplatform.foundation.controlplane.web.assistant;

import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
/**
 * 创建助手不可变版本草稿的公共请求。
 */
public record AssistantVersionRequest(
        String chatDeploymentId,
        String knowledgeSpaceId,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) {
}
