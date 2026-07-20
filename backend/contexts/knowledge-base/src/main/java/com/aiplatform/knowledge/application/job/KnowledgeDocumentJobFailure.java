package com.aiplatform.knowledge.application.job;

/**
 * 明确区分可重试的外部失败与需要人工介入的终态失败。
 */
public final class KnowledgeDocumentJobFailure extends RuntimeException {
    private final String failureCode;
    private final boolean retryable;

    private KnowledgeDocumentJobFailure(String failureCode, String message, boolean retryable) {
        super(requireText(message, "失败摘要"));
        this.failureCode = requireText(failureCode, "失败码");
        this.retryable = retryable;
    }

    public static KnowledgeDocumentJobFailure terminal(String failureCode, String message) {
        return new KnowledgeDocumentJobFailure(failureCode, message, false);
    }

    public static KnowledgeDocumentJobFailure retryable(String failureCode, String message) {
        return new KnowledgeDocumentJobFailure(failureCode, message, true);
    }

    public String failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
