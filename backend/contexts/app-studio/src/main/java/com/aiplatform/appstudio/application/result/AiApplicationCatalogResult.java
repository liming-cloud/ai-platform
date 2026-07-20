package com.aiplatform.appstudio.application.result;

import java.util.Objects;

/**
 * 应用目录投影与逐应用能力必须来自同一次服务端访问判定。
 */
public record AiApplicationCatalogResult(
        AiApplicationDetailResult application,
        ApplicationAccessMode accessMode,
        ApplicationCapabilitiesResult capabilities
) {
    public AiApplicationCatalogResult {
        application = Objects.requireNonNull(application, "应用目录投影不能为空");
        accessMode = Objects.requireNonNull(accessMode, "应用访问模式不能为空");
        capabilities = Objects.requireNonNull(capabilities, "应用能力不能为空");
    }
}
