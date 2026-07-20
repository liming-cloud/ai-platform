package com.aiplatform.appstudio.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 创建 AI 应用请求体，由表示层负责承接 HTTP 入参。
 */
public record CreateAiApplicationRequest(
        @NotBlank String applicationType,
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String modelConfigId,
        @NotEmpty List<String> knowledgeBaseIds,
        @NotBlank String systemPrompt
) {
}
