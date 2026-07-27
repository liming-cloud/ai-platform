package com.aiplatform.foundation.security.session;

/**
 * 会话无效、已撤销或内容无法形成可信请求上下文。
 */
public final class InvalidAuthenticatedSessionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidAuthenticatedSessionException() {
        super("认证会话无效");
    }
}
