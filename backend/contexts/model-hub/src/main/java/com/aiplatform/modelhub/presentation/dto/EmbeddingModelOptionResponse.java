package com.aiplatform.modelhub.presentation.dto;

public record EmbeddingModelOptionResponse(String modelConfigId, String label, String providerName,
                                           String modelCode, Integer embeddingDimension) {}
