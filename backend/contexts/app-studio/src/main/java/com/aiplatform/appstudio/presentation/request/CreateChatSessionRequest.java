package com.aiplatform.appstudio.presentation.request;

/**
 * 创建会话请求体，承接应用下新会话所需的最小输入。
 */
public record CreateChatSessionRequest(
        String title,
        String traceId
) {
}
