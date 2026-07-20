package com.aiplatform.contracts.capability.knowledge;

/**
 * 知识检索证据契约，用于审计、评测和调用方结果展示。
 */
public record KnowledgeRetrievalEvidence(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        double score,
        String retrievalSignal,
        String indexVersion
) {
    public KnowledgeRetrievalEvidence {
        knowledgeBaseId = KnowledgeRetrievalContracts.requireText(knowledgeBaseId, "知识库 ID");
        documentId = KnowledgeRetrievalContracts.requireText(documentId, "文档 ID");
        chunkId = KnowledgeRetrievalContracts.requireText(chunkId, "切片 ID");
        score = KnowledgeRetrievalContracts.requireScore(score, "召回分数");
        retrievalSignal = KnowledgeRetrievalContracts.requireText(retrievalSignal, "召回信号");
        indexVersion = KnowledgeRetrievalContracts.requireText(indexVersion, "索引版本");
    }
}
