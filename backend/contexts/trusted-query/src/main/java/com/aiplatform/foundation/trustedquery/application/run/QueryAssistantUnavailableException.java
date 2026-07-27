package com.aiplatform.foundation.trustedquery.application.run;

/**
 * 当前租户不能使用指定的已发布助手版本。
 */
public final class QueryAssistantUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueryAssistantUnavailableException() {
        super("已发布助手版本不可用");
    }
}
