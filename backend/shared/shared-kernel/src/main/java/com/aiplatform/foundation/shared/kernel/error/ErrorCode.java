package com.aiplatform.foundation.shared.kernel.error;

import java.util.Set;

/**
 * 定义稳定错误编码及其允许公开的安全元数据。
 */
public interface ErrorCode {

    String code();

    ErrorType type();

    String defaultMessage();

    boolean retryable();

    Set<String> allowedDetailFields();
}
