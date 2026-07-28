package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationScope;

/**
 * 创建模型配置的 HTTP 请求；`apiKey` 仅用于本次入站写入，响应永不包含该字段。
 */
public record ModelConfigurationCreateRequest(
        ModelConfigurationScope scope,
        String displayName,
        long catalogEntryId,
        String apiKey) {
}
