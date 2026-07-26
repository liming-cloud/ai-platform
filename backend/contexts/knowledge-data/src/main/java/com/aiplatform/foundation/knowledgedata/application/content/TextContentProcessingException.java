package com.aiplatform.foundation.knowledgedata.application.content;

/**
 * 文本内容暂存或读取发生基础设施故障。
 */
public final class TextContentProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TextContentProcessingException(Throwable cause) {
        super("文本内容处理失败", cause);
    }
}
