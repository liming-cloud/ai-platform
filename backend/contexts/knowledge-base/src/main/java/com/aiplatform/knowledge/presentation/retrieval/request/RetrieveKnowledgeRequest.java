package com.aiplatform.knowledge.presentation.retrieval.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 知识检索请求，支持在召回证据基础上可选触发答案生成。
 */
public record RetrieveKnowledgeRequest(
        @NotEmpty List<@NotBlank String> knowledgeBaseIds,
        @NotBlank String query,
        String retrievalProfile,
        String answerModel
) {}
