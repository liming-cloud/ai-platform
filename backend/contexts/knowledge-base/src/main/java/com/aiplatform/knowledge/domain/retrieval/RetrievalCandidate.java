package com.aiplatform.knowledge.domain.retrieval;

import com.aiplatform.knowledge.domain.KnowledgeDocumentChunkMetadata;
import java.util.Objects;

/**
 * 检索候选切片，统一承载向量召回、关键词召回和后续重排需要的证据字段。
 */
public record RetrievalCandidate(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String content,
        double score,
        int rank,
        RetrievalSignal signal,
        KnowledgeDocumentChunkMetadata metadata
) {
    public RetrievalCandidate {
        tenantId = requireText(tenantId, "租户 ID");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        documentId = requireText(documentId, "文档 ID");
        chunkId = requireText(chunkId, "切片 ID");
        content = requireContent(content, "切片内容");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("召回分数必须是有限数字");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("召回排名必须为正数");
        }
        Objects.requireNonNull(signal, "召回信号不能为空");
        Objects.requireNonNull(metadata, "切片元数据不能为空");
    }

    public RetrievalEvidenceIdentity evidenceIdentity() {
        return new RetrievalEvidenceIdentity(
                tenantId,
                knowledgeBaseId,
                documentId,
                chunkId,
                metadata.indexVersion()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String requireContent(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
