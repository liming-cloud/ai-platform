package com.aiplatform.foundation.assistantdelivery.interfaces.web.assistant.request;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
/**
 * 创建助手不可变版本草稿的公共请求。
 */
public record AssistantVersionRequest(
        String chatModelConfigurationId,
        String embeddingModelConfigurationId,
        String knowledgeSpaceId,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) {
}
