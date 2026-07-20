package com.aiplatform.modelhub.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DisableModelResourceRequest(
        @NotBlank(message = "停用原因不能为空")
        @Size(max = 500, message = "停用原因长度不能超过 500 个字符")
        @Pattern(regexp = "^[^\\p{Cc}]*$", message = "停用原因不能包含控制字符")
        String reason,
        @NotNull(message = "预期版本不能为空")
        @Min(value = 0, message = "预期版本不能小于 0")
        Integer expectedVersion
) {
}
