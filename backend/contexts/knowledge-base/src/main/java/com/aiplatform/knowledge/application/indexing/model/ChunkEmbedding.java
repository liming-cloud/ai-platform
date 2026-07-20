package com.aiplatform.knowledge.application.indexing.model;

/**
 * 文档切片向量化结果，用于索引用例在向量生成和索引写入之间传递显式数据。
 */
public record ChunkEmbedding(String chunkId, float[] embedding) {
}
