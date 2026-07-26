package com.aiplatform.foundation.trustedquery.api.model;

import java.util.List;
import java.util.Objects;

/**
 * 固定模型部署修订下的候选检索结果。
 */
public record CandidateSearchResult(
        List<CandidateView> candidates) {

    public CandidateSearchResult {
        Objects.requireNonNull(candidates, "候选列表不能为空");
        candidates = List.copyOf(candidates);
    }
}
