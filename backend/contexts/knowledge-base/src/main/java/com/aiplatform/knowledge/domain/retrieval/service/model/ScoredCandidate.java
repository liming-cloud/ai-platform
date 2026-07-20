package com.aiplatform.knowledge.domain.retrieval.service.model;

import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;

/**
 * 检索融合过程中的候选片段与融合分值。
 */
public record ScoredCandidate(RetrievalCandidate candidate, double score) {
    public ScoredCandidate merge(ScoredCandidate other) {
        RetrievalCandidate bestCandidate = candidate.score() >= other.candidate.score()
                ? candidate
                : other.candidate;
        return new ScoredCandidate(bestCandidate, score + other.score);
    }
}
