package com.aiplatform.appstudio.domain.port.chat.model;

import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.ValueObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天生成结果值对象，保留引用和模型调用审计属性。
 */
public record ChatCompletionResult(
        String answer,
        List<Citation> citations,
        String modelInvocationLogId,
        Map<String, String> auditAttributes,
        boolean answerable,
        String failureReason
) implements ValueObject {
    private static final String FAILURE_ANSWER = "模型调用失败，已记录调用日志，请在调试运行详情中查看错误诊断。";

    public ChatCompletionResult(
            String answer,
            List<Citation> citations,
            Map<String, String> auditAttributes
    ) {
        this(answer, citations, null, auditAttributes, true, null);
    }

    public ChatCompletionResult(
            String answer,
            List<Citation> citations,
            String modelInvocationLogId,
            Map<String, String> auditAttributes
    ) {
        this(answer, citations, modelInvocationLogId, auditAttributes, true, null);
    }

    /**
     * 构造模型调用失败结果，保留调用日志和净化后的错误诊断，供 App Studio 调试运行历史回放。
     */
    public static ChatCompletionResult failed(String modelInvocationLogId, String errorCode, String errorMessage) {
        return failed(modelInvocationLogId, errorCode, errorMessage, Map.of());
    }

    /**
     * 构造模型调用失败结果，并携带调用上下文元数据，保证即时响应与历史回放都能定位失败调用。
     */
    public static ChatCompletionResult failed(
            String modelInvocationLogId,
            String errorCode,
            String errorMessage,
            Map<String, String> metadata
    ) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.putAll(AppStudioPortContracts.normalizeTextMap(metadata, "失败审计属性"));
        attributes.put("invocationStatus", "FAILED");
        attributes.put("decisionSource", "model-invocation-failed");
        attributes.put("modelInvocationLogId", AppStudioPortContracts.requireText(modelInvocationLogId, "模型调用日志 ID"));
        attributes.put("errorCode", AppStudioPortContracts.requireText(errorCode, "错误码"));
        attributes.put("errorMessage", sanitizeDiagnostic(errorMessage));
        return new ChatCompletionResult(
                FAILURE_ANSWER,
                List.of(),
                modelInvocationLogId,
                attributes,
                false,
                FAILURE_ANSWER
        );
    }

    public ChatCompletionResult {
        answer = AppStudioPortContracts.requireText(answer, "回答内容");
        citations = AppStudioPortContracts.copyRequiredList(citations, "引用列表");
        if (citations.isEmpty()) {
            if (answerable) {
                throw new IllegalArgumentException("回答结果必须携带引用列表");
            }
        }
        modelInvocationLogId = normalizeOptionalText(modelInvocationLogId);
        if (!answerable) {
            modelInvocationLogId = AppStudioPortContracts.requireText(modelInvocationLogId, "模型调用日志 ID");
            failureReason = AppStudioPortContracts.requireText(failureReason, "失败原因");
        } else {
            failureReason = normalizeOptionalText(failureReason);
        }
        auditAttributes = AppStudioPortContracts.normalizeTextMap(auditAttributes, "审计属性");
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String sanitizeDiagnostic(String value) {
        String normalized = AppStudioPortContracts.requireText(value, "错误信息");
        return normalized
                .replaceAll("(?i)(api[-_ ]?key|token|secret|credential)\\s*=\\s*\\S+", "[redacted]")
                .replace("***", "[redacted]");
    }
}
