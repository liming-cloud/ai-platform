package com.aiplatform.foundation.knowledgedata.application.content;

/**
 * 对象存储当前无法提供受治理操作。
 */
public final class ObjectStorageUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ObjectStorageUnavailableException() {
        super("对象存储当前不可用");
    }

    public ObjectStorageUnavailableException(Throwable cause) {
        super("对象存储当前不可用", cause);
    }
}
