package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.CitationResult;
import com.aiplatform.appstudio.domain.Citation;

public record CitationResponse(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String title,
        String snippet,
        String locator,
        double score
) {
    public static CitationResponse from(CitationResult result) {
        return new CitationResponse(
                result.knowledgeBaseId(),
                result.documentId(),
                result.chunkId(),
                result.title(),
                result.snippet(),
                result.locator(),
                result.score()
        );
    }

    public static CitationResponse from(Citation citation) {
        return new CitationResponse(
                citation.knowledgeBaseId(),
                citation.documentId(),
                citation.chunkId(),
                citation.documentName(),
                citation.fragment(),
                citation.location(),
                citation.score()
        );
    }
}
