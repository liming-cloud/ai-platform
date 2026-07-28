package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 仅用于本地研发的版本化主密钥选择；生产环境必须提供 KMS 或 Vault 的密钥提供者。
 */
@ConfigurationProperties("foundation.model-credentials")
public class ModelCredentialProperties {

    private String localDevelopmentKeyVersion;

    public String getLocalDevelopmentKeyVersion() {
        return localDevelopmentKeyVersion;
    }

    public void setLocalDevelopmentKeyVersion(String localDevelopmentKeyVersion) {
        this.localDevelopmentKeyVersion = localDevelopmentKeyVersion;
    }
}
