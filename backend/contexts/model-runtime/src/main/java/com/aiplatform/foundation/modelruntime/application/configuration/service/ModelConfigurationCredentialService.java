package com.aiplatform.foundation.modelruntime.application.configuration.service;

import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationCredentialWriter;
import com.aiplatform.foundation.modelruntime.application.configuration.port.out.ModelConfigurationTransaction;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.util.Objects;

/**
 * 原子创建模型配置和加密凭据，API Key 不进入配置聚合或返回值。
 */
public final class ModelConfigurationCredentialService {

    private final ModelConfigurationService configurations;
    private final ModelConfigurationCredentialWriter credentials;
    private final ModelConfigurationTransaction transactions;

    public ModelConfigurationCredentialService(
            ModelConfigurationService configurations,
            ModelConfigurationCredentialWriter credentials,
            ModelConfigurationTransaction transactions) {
        this.configurations = Objects.requireNonNull(configurations, "模型配置用例不能为空");
        this.credentials = Objects.requireNonNull(credentials, "模型配置凭据写入器不能为空");
        this.transactions = Objects.requireNonNull(transactions, "模型配置事务不能为空");
    }

    public ModelConfiguration create(
            TrustedRequestContext context,
            ModelConfigurationScope scope,
            String displayName,
            ControlledModelCatalogEntryId catalogEntryId,
            SecretValue apiKey) {
        Objects.requireNonNull(apiKey, "API Key不能为空");
        return transactions.execute(() -> {
            var configuration = configurations.create(
                    context,
                    scope,
                    displayName,
                    catalogEntryId);
            credentials.rotate(configuration.id(), apiKey);
            return configuration;
        });
    }
}
