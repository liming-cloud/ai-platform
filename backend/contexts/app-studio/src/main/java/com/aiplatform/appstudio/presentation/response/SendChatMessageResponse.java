package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ChatMessageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SendChatMessageResponse(
        String messageId,
        String userMessageId,
        String assistantMessageId,
        String sessionId,
        String applicationId,
        String answer,
        boolean answerable,
        List<CitationResponse> citations,
        int filteredCitationCount,
        List<RetrievalEvidenceResponse> evidences,
        String noAnswerReason,
        String traceId,
        String modelInvocationLogId,
        String providerCode,
        String modelCode,
        String decisionSource,
        String invocationStatus,
        Integer inputTokens,
        Integer outputTokens,
        Integer latencyMs,
        String sourceContext,
        String sourceResourceType,
        String sourceResourceId,
        String errorCode,
        String errorMessage,
        Instant createdAt
) {
    public SendChatMessageResponse {
        if (filteredCitationCount < 0) {
            throw new IllegalArgumentException("过滤引用数量不能小于 0");
        }
        // 兼容保留字段，但禁止通过聊天 HTTP 响应暴露 ACL 过滤规模。
        filteredCitationCount = 0;
    }

    public static SendChatMessageResponse from(ChatMessageResult result) {
        Map<String, Object> metadata = result.metadata();
        return new SendChatMessageResponse(
                result.assistantMessageId(),
                result.userMessageId(),
                result.assistantMessageId(),
                result.sessionId(),
                result.applicationId(),
                result.answer(),
                result.answerable(),
                result.citations().stream().map(CitationResponse::from).toList(),
                0,
                result.evidences().stream().map(RetrievalEvidenceResponse::from).toList(),
                result.noAnswerReason(),
                text(metadata, "traceId"),
                text(metadata, "modelInvocationLogId"),
                text(metadata, "providerCode"),
                text(metadata, "modelCode"),
                text(metadata, "decisionSource"),
                text(metadata, "invocationStatus"),
                integer(metadata, "inputTokens"),
                integer(metadata, "outputTokens"),
                integer(metadata, "latencyMs"),
                text(metadata, "sourceContext"),
                text(metadata, "sourceResourceType"),
                text(metadata, "sourceResourceId"),
                text(metadata, "errorCode"),
                text(metadata, "errorMessage"),
                result.createdAt()
        );
    }

    private static String text(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
