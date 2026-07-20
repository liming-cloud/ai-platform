package com.aiplatform.modelhub.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 创建模型配置请求 DTO，options 保持结构化对象，禁止前端拼接供应商专有配置字符串。
 */
public record CreateModelConfigRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String requestId,
        @NotBlank String modelCode,
        @NotBlank String modelName,
        @NotBlank String modelType,
        @NotNull @Min(1) Integer contextWindow,
        @Min(1) Integer embeddingDimension,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 8) BigDecimal inputPricePer1k,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 8) BigDecimal outputPricePer1k,
        @NotNull Map<String, Object> options
) {
}
