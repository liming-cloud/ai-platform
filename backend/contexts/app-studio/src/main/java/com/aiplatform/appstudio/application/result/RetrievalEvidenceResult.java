package com.aiplatform.appstudio.application.result;

import com.aiplatform.contracts.capability.knowledge.KnowledgeRetrievalEvidence;

/**
 * 召回证据结果模型，向表示层传递检索侧证据。
 */
public record RetrievalEvidenceResult(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        double score,
        String retrievalSignal,
        String indexVersion
) {
    public static RetrievalEvidenceResult from(KnowledgeRetrievalEvidence evidence) {
        return new RetrievalEvidenceResult(
                evidence.knowledgeBaseId(),
                evidence.documentId(),
                evidence.chunkId(),
                evidence.score(),
                evidence.retrievalSignal(),
                evidence.indexVersion()
        );
    }
}
