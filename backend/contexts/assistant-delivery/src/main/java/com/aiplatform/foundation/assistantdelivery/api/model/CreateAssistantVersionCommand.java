package com.aiplatform.foundation.assistantdelivery.api.model;

import com.aiplatform.foundation.assistantdelivery.domain.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.RefusalPolicy;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;
import java.util.UUID;

/**
 * 以当前已发布模型和活动索引创建助手版本草稿。
 */
public record CreateAssistantVersionCommand(
        TrustedRequestContext context,
        String idempotencyKey,
        UUID assistantId,
        UUID chatDeploymentId,
        UUID knowledgeSpaceId,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) {

    public CreateAssistantVersionCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(assistantId, "助手标识不能为空");
        Objects.requireNonNull(chatDeploymentId, "对话部署标识不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(systemInstruction, "系统指令不能为空");
        Objects.requireNonNull(citationPolicy, "引用策略不能为空");
        Objects.requireNonNull(refusalPolicy, "拒答策略不能为空");
        if (idempotencyKey.isBlank()
                || !idempotencyKey.equals(idempotencyKey.trim())
                || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
    }
}
