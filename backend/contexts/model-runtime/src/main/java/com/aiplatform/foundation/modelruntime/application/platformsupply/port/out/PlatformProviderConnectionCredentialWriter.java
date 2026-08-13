package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.security.secret.SecretValue;

/** 将平台私有厂商凭据写入受管密钥存储。 */
@FunctionalInterface
public interface PlatformProviderConnectionCredentialWriter {

    void write(CredentialReference reference, SecretValue apiKey);
}
