package com.aiplatform.foundation.shared.kernel.error;

/**
 * 平台允许公开的稳定错误类别。
 */
public enum ErrorType {
    REQUEST,
    AUTHENTICATION,
    AUTHORIZATION,
    RESOURCE,
    CONFLICT,
    QUOTA,
    DEPENDENCY_UNAVAILABLE,
    DEPENDENCY_RESPONSE,
    SYSTEM
}
