package com.aiplatform.foundation.knowledgedata.application.content;

/**
 * 对象存储返回的内容元数据与受治理事实不一致。
 */
public final class TextContentIntegrityException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TextContentIntegrityException() {
        super("文本内容完整性核验失败");
    }
}
