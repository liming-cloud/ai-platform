package com.aiplatform.foundation.assistantdelivery.domain.assistant.testrun.model;

import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.CitationPolicy;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedChatModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.GovernedEmbeddingModelBinding;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.model.RefusalPolicy;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;

/**
 * 草稿测试启动时冻结的助手版本、知识索引和受控模型依赖。
 */
public record AssistantTestTarget(
        long assistantVersionId,
        long knowledgeSpaceId,
        long indexVersionId,
        long indexVersionRevision,
        GovernedChatModelBinding governedChat,
        GovernedEmbeddingModelBinding governedEmbedding,
        String systemInstruction,
        int maxOutputTokens,
        int retrievalLimit,
        double minimumScore,
        CitationPolicy citationPolicy,
        RefusalPolicy refusalPolicy) implements ValueObject {

    private static final int MAX_INSTRUCTION_CODE_POINTS = 4000;

    public AssistantTestTarget {
        if (assistantVersionId <= 0 || knowledgeSpaceId <= 0 || indexVersionId <= 0
                || indexVersionRevision < 1) {
            throw new IllegalArgumentException("助手测试冻结的版本与索引标识必须有效");
        }
        Objects.requireNonNull(governedChat, "受控对话模型绑定不能为空");
        Objects.requireNonNull(governedEmbedding, "受控嵌入模型绑定不能为空");
        Objects.requireNonNull(systemInstruction, "系统指令不能为空");
        if (systemInstruction.isBlank()
                || !systemInstruction.equals(systemInstruction.trim())
                || systemInstruction.codePointCount(0, systemInstruction.length())
                > MAX_INSTRUCTION_CODE_POINTS) {
            throw new IllegalArgumentException("系统指令格式不正确");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > governedChat.outputLimit()) {
            throw new IllegalArgumentException("回答上限必须在受控对话模型允许范围内");
        }
        if (retrievalLimit < 1 || retrievalLimit > 20
                || !Double.isFinite(minimumScore) || minimumScore < 0.0 || minimumScore > 1.0) {
            throw new IllegalArgumentException("检索参数不正确");
        }
        if (citationPolicy != CitationPolicy.REQUIRED
                || refusalPolicy != RefusalPolicy.INSUFFICIENT_EVIDENCE) {
            throw new IllegalArgumentException("草稿测试必须要求引用并在证据不足时拒答");
        }
    }
}
