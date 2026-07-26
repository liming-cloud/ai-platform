package com.aiplatform.foundation.modelruntime.application.runtime;

/**
 * 模型部署不满足安全构造条件或缺少对应扩展时抛出。
 */
public final class ModelRuntimeUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelRuntimeUnavailableException(String message) {
        super(message);
    }
}
