package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;

/**
 * 平台受控模型目录的创建请求，厂家类型只能使用受控枚举值。
 */
public record ControlledModelCatalogEntryCreateRequest(
        ProviderKind providerKind,
        URI endpoint,
        String modelCode,
        ModelType modelType) {
}
