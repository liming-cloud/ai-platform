package com.aiplatform.knowledge.domain.port.indexing;

/**
 * 已原子激活的知识库索引快照。模型与版本必须成对读取，避免模型切换期间混用向量空间。
 */
public record ActiveKnowledgeIndex(String embeddingModel, String indexVersion) {
    public ActiveKnowledgeIndex {
        embeddingModel = requireText(embeddingModel, "Active Embedding 模型");
        indexVersion = requireText(indexVersion, "Active 索引版本");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
