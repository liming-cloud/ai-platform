package com.aiplatform.infra.springai.model.provider.local;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 本地确定性 ChatModel，用于无外部模型依赖的产品闭环验收。
 */
public class LocalDeterministicChatModel implements ChatModel {
    private static final int MAX_FRAGMENT_LENGTH = 420;
    private static final String INSUFFICIENT_EVIDENCE_ANSWER = "证据不足：当前未检索到可用于回答的引用原文。";
    private static final String HEALTH_ANSWER = "本地确定性运行时健康检查通过。";
    private final ChatOptions defaultOptions;

    public LocalDeterministicChatModel(ChatOptions defaultOptions) {
        this.defaultOptions = Objects.requireNonNull(defaultOptions, "默认 ChatOptions 不能为空");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Objects.requireNonNull(prompt, "聊天提示词不能为空");
        String answer = deterministicAnswer(prompt.getContents());
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }

    @Override
    public ChatOptions getOptions() {
        return defaultOptions;
    }

    private static String deterministicAnswer(String promptContent) {
        String normalizedPrompt = normalize(promptContent);
        if ("ping".equalsIgnoreCase(normalizedPrompt)) {
            return HEALTH_ANSWER;
        }
        if (!normalizedPrompt.contains("引用原文：")) {
            return INSUFFICIENT_EVIDENCE_ANSWER;
        }
        String question = extractBetween(normalizedPrompt, "用户问题：", "请优先基于以下引用原文直接作答");
        String citationText = extractBetween(normalizedPrompt, "引用原文：", "召回元数据：");
        String fragment = firstCitationLine(citationText);
        if (fragment.isBlank()) {
            return INSUFFICIENT_EVIDENCE_ANSWER;
        }
        return "本地确定性回答：已根据引用原文生成回答。"
                + (question.isBlank() ? "" : "\n问题：" + truncate(question, 160))
                + "\n结论：" + truncate(fragment, MAX_FRAGMENT_LENGTH)
                + "\n证据出处：见本次响应返回的引用列表。";
    }

    private static String extractBetween(String value, String startMarker, String endMarker) {
        int start = value.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        int contentStart = start + startMarker.length();
        int end = value.indexOf(endMarker, contentStart);
        if (end < 0) {
            end = value.length();
        }
        return value.substring(contentStart, end).trim();
    }

    private static String firstCitationLine(String value) {
        for (String line : value.split("\\R")) {
            String normalized = normalize(line).trim();
            if (normalized.startsWith("原文：")) {
                return normalized.replaceFirst("^原文：", "").trim();
            }
        }
        return "";
    }

    private static String truncate(String value, int maxLength) {
        String normalized = normalize(value);
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\r', '\n').trim();
    }
}
