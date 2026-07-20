package com.aiplatform.knowledge.domain.port.ingest.model;

import java.util.List;
import java.util.Objects;

/**
 * 文档解析和切块后的标准领域结果，屏蔽 Spring AI 或其他实现细节。
 */
public record ProcessedKnowledgeDocument(
        String processorName,
        String chunkStrategyVersion,
        List<KnowledgeDocumentChunkDraft> chunks
) {
    public ProcessedKnowledgeDocument {
        processorName = requireText(processorName, "文档处理器名称");
        chunkStrategyVersion = requireText(chunkStrategyVersion, "切块策略版本");
        Objects.requireNonNull(chunks, "切片草稿列表不能为空");
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("切片草稿列表不能为空");
        }
        for (KnowledgeDocumentChunkDraft chunk : chunks) {
            if (chunk == null) {
                throw new IllegalArgumentException("切片草稿不能为空");
            }
        }
        chunks = List.copyOf(chunks);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
