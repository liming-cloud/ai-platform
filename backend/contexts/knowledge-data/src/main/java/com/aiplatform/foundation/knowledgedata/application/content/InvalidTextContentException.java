package com.aiplatform.foundation.knowledgedata.application.content;

/**
 * 原始文本为空、超限或不是严格 UTF-8。
 */
public final class InvalidTextContentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTextContentException(String message) {
        super(message);
    }

    public InvalidTextContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
