package com.aiplatform.identity.application.error;

import com.aiplatform.shared.kernel.ErrorCode;

/**
 * 身份与访问上下文应用层错误码，面向 API 返回、日志和审计统一映射。
 */
public final class IdentityErrorCodes {
    public static final ErrorCode AUTH_FAILED = ErrorCode.of("AIP-IAM-AUTHFAILED", "登录名或密码错误");
    public static final ErrorCode LOGIN_RATE_LIMITED = ErrorCode.of(
            "AIP-IAM-LOGINRATELIMITED",
            "登录尝试过于频繁，请稍后重试"
    );
    public static final ErrorCode AUTH_PROTECTION_UNAVAILABLE = ErrorCode.of(
            "AIP-IAM-AUTHPROTECTIONUNAVAILABLE",
            "登录服务暂时不可用，请稍后重试"
    );
    public static final ErrorCode USER_DISABLED = ErrorCode.of("AIP-IAM-USERDISABLED", "用户不可登录");
    public static final ErrorCode SESSION_INVALID = ErrorCode.of("AIP-IAM-SESSIONINVALID", "登录态无效或已过期");
    public static final ErrorCode RESOURCE_NOT_VISIBLE = ErrorCode.of("AIP-IAM-RESOURCE-5004", "资源不可见");
    public static final ErrorCode PERMISSION_REQUEST_DECISION_DENIED = ErrorCode.of(
            "AIP-IAM-PERMISSIONREQUEST-PERMISSIONDENIED",
            "权限申请决策被拒绝"
    );

    private IdentityErrorCodes() {
    }
}
