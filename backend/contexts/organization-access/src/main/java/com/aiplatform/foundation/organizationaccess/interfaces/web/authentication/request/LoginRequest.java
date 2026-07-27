package com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.request;

import java.util.Arrays;

/**
 * 登录请求载体，禁止在字符串表示中输出密码。
 */
public record LoginRequest(
        String tenantCode,
        String loginName,
        char[] password) {

    public LoginRequest {
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
        return "LoginRequest[tenantCode=" + tenantCode
                + ", loginName=" + loginName
                + ", password=[已脱敏]]";
    }
}
