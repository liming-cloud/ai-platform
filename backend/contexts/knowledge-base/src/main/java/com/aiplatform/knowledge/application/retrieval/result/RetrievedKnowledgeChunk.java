package com.aiplatform.knowledge.application.retrieval.result;

import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.retrieval.RetrievalEvidenceIdentity;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import java.util.Objects;

/**
 * 通过权限校验后的知识切片证据，用于引用展示、审计和后续 Prompt 组装。
 */
public record RetrievedKnowledgeChunk(
        String tenantId,
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String content,
        double score,
        RetrievalSignal signal,
        String sourceUri,
        String titlePath,
        Integer paragraphNo,
        String tableCellRange,
        boolean authoritative,
        String indexVersion
) {
    public RetrievedKnowledgeChunk {
        tenantId = requireText(tenantId, "tenantId");
        knowledgeBaseId = requireText(knowledgeBaseId, "knowledgeBaseId");
        documentId = requireText(documentId, "documentId");
        chunkId = requireText(chunkId, "chunkId");
        content = requireText(content, "content");
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("score必须在 0 到 1 之间");
        }
        Objects.requireNonNull(signal, "召回信号不能为空");
        sourceUri = requireText(sourceUri, "sourceUri");
        titlePath = requireText(titlePath, "titlePath");
        tableCellRange = tableCellRange == null ? null : normalizeOptionalText(tableCellRange, "tableCellRange");
        indexVersion = requireText(indexVersion, "indexVersion");
    }

    public static RetrievedKnowledgeChunk from(RetrievalCandidate candidate) {
        return new RetrievedKnowledgeChunk(
                candidate.tenantId(),
                candidate.knowledgeBaseId(),
                candidate.documentId(),
                candidate.chunkId(),
                candidate.content(),
                candidate.score(),
                candidate.signal(),
                candidate.metadata().sourceUri(),
                candidate.metadata().titlePath(),
                candidate.metadata().paragraphNo(),
                candidate.metadata().tableCellRange(),
                candidate.metadata().authoritative(),
                candidate.metadata().indexVersion()
        );
    }

    public RetrievalEvidenceIdentity evidenceIdentity() {
        return new RetrievalEvidenceIdentity(
                tenantId,
                knowledgeBaseId,
                documentId,
                chunkId,
                indexVersion
        );
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value, String fieldName) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
