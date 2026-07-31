package com.aiplatform.foundation.assistantdelivery.application.assistant.testrun.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 发起一次草稿助手可信测试的应用命令。
 */
public record RunAssistantDraftTestCommand(
        TrustedRequestContext context,
        String assistantVersionId,
        String question) {

    public RunAssistantDraftTestCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        requireText(assistantVersionId, "助手版本标识不能为空");
        requireText(question, "测试问题不能为空");
    }

    private static void requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
