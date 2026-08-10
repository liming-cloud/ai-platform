package com.aiplatform.foundation.modelruntime.application.provider.port.out;

import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.security.secret.SecretValue;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * 将厂商连接 API Key 写入受保护存储；实现不得回传或记录秘密内容。
 */
public interface ProviderConnectionCredentialWriter {

    void write(TenantId tenantId, CredentialReference reference, SecretValue apiKey);
}
