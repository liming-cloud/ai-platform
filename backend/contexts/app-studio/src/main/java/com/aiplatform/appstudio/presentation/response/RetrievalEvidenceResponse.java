package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.RetrievalEvidenceResult;
import com.aiplatform.appstudio.domain.port.debug.model.AppStudioDebugRunEvidenceSnapshot;

public record RetrievalEvidenceResponse(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        double score,
        String retrievalSignal,
        String indexVersion
) {
    public static RetrievalEvidenceResponse from(RetrievalEvidenceResult result) {
        return new RetrievalEvidenceResponse(
                result.knowledgeBaseId(),
                result.documentId(),
                result.chunkId(),
                result.score(),
                result.retrievalSignal(),
                result.indexVersion()
        );
    }

    public static RetrievalEvidenceResponse from(AppStudioDebugRunEvidenceSnapshot evidence) {
        return new RetrievalEvidenceResponse(
                evidence.knowledgeBaseId(),
                evidence.documentId(),
                evidence.chunkId(),
                evidence.score(),
                evidence.retrievalSignal(),
                evidence.indexVersion()
        );
    }
}
