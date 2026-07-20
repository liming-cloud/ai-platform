package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 更新 AI 应用请求，要求前端显式提交完整配置快照，避免局部字段静默沿用旧值。
 */
public record UpdateAiApplicationRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String modelConfigId,
        @NotEmpty List<String> knowledgeBaseIds,
        @NotBlank String systemPrompt
) {
}
