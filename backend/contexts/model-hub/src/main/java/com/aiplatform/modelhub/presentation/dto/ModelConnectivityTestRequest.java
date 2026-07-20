package com.aiplatform.modelhub.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 连通性探测请求必须携带客户端看到的模型配置版本。
 */
public record ModelConnectivityTestRequest(
        @NotNull @Min(0) Integer expectedVersion
) {
}
