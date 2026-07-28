package com.aiplatform.foundation.modelruntime.application.configuration.dto;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import java.time.Instant;
import java.util.Objects;

/**
 * 为模型选择器提供的安全列表项，不包含 API Key、密文或端点。
 */
public record ModelConfigurationListItem(
        String id,
        String scope,
        String displayName,
        String catalogEntryId,
        ModelType modelType,
        String status,
        long version,
        boolean credentialConfigured,
        Instant createdAt) {

    public static ModelConfigurationListItem from(
            ModelConfiguration configuration,
            ModelType modelType,
            boolean credentialConfigured) {
        Objects.requireNonNull(configuration, "模型配置不能为空");
        return new ModelConfigurationListItem(
                configuration.id().toString(),
                configuration.scope().name(),
                configuration.displayName(),
                configuration.catalogEntryId().toString(),
                Objects.requireNonNull(modelType, "模型类型不能为空"),
                configuration.status().name(),
                configuration.version(),
                credentialConfigured,
                configuration.createdAt());
    }
}
