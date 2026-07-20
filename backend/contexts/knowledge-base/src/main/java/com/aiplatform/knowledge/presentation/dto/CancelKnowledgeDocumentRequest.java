package com.aiplatform.knowledge.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelKnowledgeDocumentRequest(
        @NotBlank @Size(min = 3, max = 300) String reason
) {}
