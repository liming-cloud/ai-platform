package com.aiplatform.foundation.organizationaccess.application.port.out;

/**
 * 密码摘要验证端口。
 */
@FunctionalInterface
public interface PasswordVerifier {

    boolean matches(char[] candidate, String encodedPassword);
}
