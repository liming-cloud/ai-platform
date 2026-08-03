package com.aiplatform.foundation.security.web;

/**
 * 浏览器范围会话 cookie 的固定名称。
 *
 * <p>同一次浏览器登录可持有两枚 cookie，但平台与租户解析器只读取各自名称，避免把范围选择下沉为
 * 可选请求参数或通用 token。</p>
 */
public final class BrowserSessionCookieNames {

    public static final String TENANT_SESSION = "__Host-foundation-tenant-session";
    public static final String PLATFORM_SESSION = "__Host-foundation-platform-session";
    public static final String CSRF = "__Host-foundation-csrf";

    /**
     * 判断 cookie 是否承载可恢复身份的浏览器会话。
     */
    public static boolean isSessionCookie(String name) {
        return TENANT_SESSION.equals(name) || PLATFORM_SESSION.equals(name);
    }

    private BrowserSessionCookieNames() {
    }
}
