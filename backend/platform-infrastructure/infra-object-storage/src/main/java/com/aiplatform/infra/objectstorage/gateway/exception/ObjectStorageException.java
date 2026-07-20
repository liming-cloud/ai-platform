package com.aiplatform.infra.objectstorage.gateway.exception;

/**
 * 对象存储基础设施调用失败。
 */
public class ObjectStorageException extends RuntimeException {
    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
