package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 当前主体缺少助手操作权限。
 */
public final class AssistantPermissionDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantPermissionDeniedException() {
        super("当前主体没有助手操作权限");
    }
}
