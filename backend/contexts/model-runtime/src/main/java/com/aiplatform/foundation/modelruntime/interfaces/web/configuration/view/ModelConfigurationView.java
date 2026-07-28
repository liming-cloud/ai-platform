package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.view;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import java.time.Instant;

/**
 * 模型配置的安全响应，不包含 API Key、密文、nonce、指纹或密钥引用。
 */
public record ModelConfigurationView(
        String id,
        String scope,
        String displayName,
        String catalogEntryId,
        String status,
        long version,
        boolean credentialConfigured,
        Instant createdAt) {

    public static ModelConfigurationView created(ModelConfiguration configuration) {
        return new ModelConfigurationView(
                configuration.id().toString(),
                configuration.scope().name(),
                configuration.displayName(),
                configuration.catalogEntryId().toString(),
                configuration.status().name(),
                configuration.version(),
                true,
                configuration.createdAt());
    }
}
