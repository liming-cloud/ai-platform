package com.aiplatform.appstudio.application.error;

import com.aiplatform.shared.kernel.ErrorCode;

public final class AppStudioErrorCodes {
    public static final ErrorCode AUTHORIZATION_DENIED =
            ErrorCode.of("AIP-IAM-AUTHZ-5003", "权限不足");
    public static final ErrorCode RESOURCE_NOT_VISIBLE =
            ErrorCode.of("AIP-IAM-RESOURCE-5004", "资源不可见");
    public static final ErrorCode RESOURCE_ACL_REQUESTABLE =
            ErrorCode.of("AIP-IAM-AUTHZ-5003-REQUESTABLE", "当前操作可申请资源授权");
    public static final ErrorCode MODEL_POLICY_DENIED =
            ErrorCode.of("AIP-MDL-POLICY-5007", "模型策略拒绝");
    public static final ErrorCode PUBLISH_BLOCKED =
            ErrorCode.of("AIP-APP-PUBLISH-6001", "应用发布预检未通过");
    public static final ErrorCode DUPLICATE_MESSAGE_REQUEST =
            ErrorCode.of("AIP-COMMON-VALIDATION-2001", "重复消息请求");
    public static final ErrorCode CHAT_RUN_IN_PROGRESS =
            ErrorCode.of("AIP-APP-CHAT-6002", "聊天请求处理中");
    public static final ErrorCode CHAT_RUN_FAILED =
            ErrorCode.of("AIP-APP-CHAT-6003", "聊天请求执行失败");
    public static final ErrorCode CHAT_RUN_UNCERTAIN =
            ErrorCode.of("AIP-APP-CHAT-6004", "聊天模型结果状态不确定");
    public static final ErrorCode CHAT_RUN_REPLAY_CONFLICT =
            ErrorCode.of("AIP-APP-CHAT-6005", "聊天最终化死信当前不可重放");
    public static final ErrorCode SESSION_RELEASE_CHANGED =
            ErrorCode.of("AIP-APP-CHAT-SESSION-CONFLICT", "应用已发布新版本，请新建会话后继续");
    public static final ErrorCode CATALOG_WINDOW_EXCEEDED =
            ErrorCode.of("AIP-APP-CATALOG-6006", "应用目录超过兼容窗口");

    private AppStudioErrorCodes() {
    }
}
