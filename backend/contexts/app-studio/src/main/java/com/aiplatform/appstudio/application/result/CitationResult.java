package com.aiplatform.appstudio.application.result;

import com.aiplatform.appstudio.domain.Citation;

/**
 * 引用结果模型，向表示层传递回答引用片段。
 */
public record CitationResult(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String title,
        String snippet,
        String locator,
        double score
) {
    public static CitationResult from(Citation citation) {
        return new CitationResult(
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
