package com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.request;

import java.util.Arrays;

/**
 * 平台治理登录请求；租户和成员身份均由已验证主体及平台授权恢复，禁止由请求提交。
 */
public record PlatformLoginRequest(String loginName, char[] password) {

    public PlatformLoginRequest {
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
        return "PlatformLoginRequest[loginName=" + loginName + ", password=[已脱敏]]";
    }
}
