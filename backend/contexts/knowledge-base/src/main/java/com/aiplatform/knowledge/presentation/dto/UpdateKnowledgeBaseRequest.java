package com.aiplatform.knowledge.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        String name,
        @NotBlank(message = "知识库可见性不能为空")
        @Pattern(regexp = "public|department|project|private|members|roles")
        String visibility,
        String description,
        @NotBlank(message = "知识库向量模型配置不能为空")
        String embeddingModelConfigId
) {
}
