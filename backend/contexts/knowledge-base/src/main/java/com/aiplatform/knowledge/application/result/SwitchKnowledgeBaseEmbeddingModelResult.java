package com.aiplatform.knowledge.application.result;

import com.aiplatform.shared.kernel.ValueObject;

/**
 * 知识库向量模型切换结果，返回新挂载模型和被重新索引的文档数量。
 */
public record SwitchKnowledgeBaseEmbeddingModelResult(
        String knowledgeBaseId,
        String embeddingModelConfigId,
        int reindexJobCount
) implements ValueObject {
}
