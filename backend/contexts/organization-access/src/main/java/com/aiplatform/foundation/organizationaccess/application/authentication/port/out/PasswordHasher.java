package com.aiplatform.foundation.organizationaccess.application.authentication.port.out;

/** 为新建或重置凭据生成不可逆摘要的端口。 */
@FunctionalInterface
public interface PasswordHasher {

    String encode(char[] password);
}
