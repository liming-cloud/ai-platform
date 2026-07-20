package com.aiplatform.modelhub.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 更新模型配置请求 DTO，expectedVersion 是客户端最近读取版本，防止陈旧表单覆盖新状态。
 */
public record UpdateModelConfigRequest(
        @NotBlank String modelCode,
        @NotBlank String modelName,
        @NotBlank String modelType,
        @NotNull @Min(1) Integer contextWindow,
        @Min(1) Integer embeddingDimension,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 8) BigDecimal inputPricePer1k,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 8) BigDecimal outputPricePer1k,
        @NotNull Map<String, Object> options,
        @NotNull @Min(0) Integer expectedVersion
) {
}
