package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import com.aiplatform.foundation.security.secret.EnvironmentSecretEncryptionKeyProvider;
import com.aiplatform.foundation.security.secret.SecretEncryptionKeyProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 仅在显式选择本地研发主密钥版本时装配环境变量密钥提供者。
 */
@AutoConfiguration(before = ModelRuntimePersistenceAutoConfiguration.class)
@ConditionalOnProperty(name = "foundation.model-credentials.local-development-key-version")
@EnableConfigurationProperties(ModelCredentialProperties.class)
public class ModelRuntimeCredentialAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SecretEncryptionKeyProvider secretEncryptionKeyProvider(
            ModelCredentialProperties properties) {
        return new EnvironmentSecretEncryptionKeyProvider(
                properties.getLocalDevelopmentKeyVersion());
    }
}
