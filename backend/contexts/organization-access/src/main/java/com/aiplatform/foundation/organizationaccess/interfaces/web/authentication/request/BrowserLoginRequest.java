package com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.request;

import java.util.Arrays;

/**
 * 统一浏览器登录请求；租户编码只在建立租户范围会话时使用。
 */
public record BrowserLoginRequest(
        String tenantCode,
        String loginName,
        char[] password) {

    public BrowserLoginRequest {
        password = password == null ? null : password.clone();
    }

    public char[] passwordCopy() {
        if (password == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return password.clone();
    }

    public void clearPassword() {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public char[] password() {
        return password == null ? null : password.clone();
    }

    @Override
    public String toString() {
        return "BrowserLoginRequest[tenantCode=" + tenantCode
                + ", loginName=" + loginName + ", password=[已脱敏]]";
    }
}
