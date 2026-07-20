package com.aiplatform.knowledge.domain;

/**
 * DOCUMENT_INDEX 任务在创建时冻结的候选索引事实。重试只能复用这些事实，不能另造版本。
 */
public record DocumentIndexJobTarget(
        String rebuildId,
        String modelConfigId,
        String embeddingModel,
        String indexVersion,
        String operatorType
) {
    public DocumentIndexJobTarget {
        rebuildId = requireText(rebuildId, "重建计划 ID");
        modelConfigId = requireText(modelConfigId, "模型配置 ID");
        embeddingModel = requireText(embeddingModel, "Embedding 模型");
        indexVersion = requireText(indexVersion, "索引版本");
        operatorType = requireText(operatorType, "操作人类型");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
