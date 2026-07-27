package com.aiplatform.foundation.trustedquery.application.run;

import com.aiplatform.foundation.assistantdelivery.api.model.version.PublishedAssistantVersionSnapshot;
import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidate;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatMessage;
import com.aiplatform.foundation.modelruntime.api.model.chat.ChatRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 以固定顺序和大小预算组装仅包含已授权证据的对话消息。
 */
final class QueryPromptBuilder {

    private static final int MAX_EVIDENCE_CODE_POINTS = 24_000;
    private static final int MAX_CHUNK_CODE_POINTS = 2_000;

    private QueryPromptBuilder() {
    }

    static List<ChatMessage> build(
            PublishedAssistantVersionSnapshot assistant,
            String question,
            List<IndexCandidate> evidence) {
        Objects.requireNonNull(assistant, "助手版本不能为空");
        Objects.requireNonNull(question, "问题不能为空");
        if (evidence == null || evidence.isEmpty()) {
            throw new IllegalArgumentException("生成回答前必须存在授权证据");
        }
        var system = assistant.systemInstruction()
                + "\n只允许依据用户消息中的编号证据回答。"
                + "每个事实必须对应证据编号；证据没有包含的信息不得推测。";
        var user = new StringBuilder()
                .append("问题：")
                .append(question)
                .append("\n\n授权证据：");
        var remaining = MAX_EVIDENCE_CODE_POINTS;
        for (var index = 0; index < evidence.size() && remaining > 0; index++) {
            var content = truncate(
                    evidence.get(index).content(),
                    Math.min(MAX_CHUNK_CODE_POINTS, remaining));
            user.append("\n[证据")
                    .append(index + 1)
                    .append("] ")
                    .append(content);
            remaining -= content.codePointCount(0, content.length());
        }
        var messages = new ArrayList<ChatMessage>(2);
        messages.add(new ChatMessage(ChatRole.SYSTEM, system));
        messages.add(new ChatMessage(ChatRole.USER, user.toString()));
        return List.copyOf(messages);
    }

    static String quote(String content) {
        return truncate(content, 1000);
    }

    private static String truncate(String value, int maximumCodePoints) {
        Objects.requireNonNull(value, "证据内容不能为空");
        if (maximumCodePoints < 1 || value.isBlank()) {
            throw new IllegalArgumentException("证据内容格式不正确");
        }
        if (value.codePointCount(0, value.length()) <= maximumCodePoints) {
            return value;
        }
        var end = value.offsetByCodePoints(0, maximumCodePoints);
        return value.substring(0, end);
    }
}
