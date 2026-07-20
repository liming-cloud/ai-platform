package com.aiplatform.knowledge.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 创建知识库的入口参数，visibility 决定后续文档召回时的默认可见范围。
 */
public record CreateKnowledgeBaseRequest(
        @NotBlank String name,
        @NotBlank
        @Pattern(regexp = "public|department|project|private|members|roles")
        String visibility,
        String description,
        @NotBlank String embeddingModelConfigId
) {
}
