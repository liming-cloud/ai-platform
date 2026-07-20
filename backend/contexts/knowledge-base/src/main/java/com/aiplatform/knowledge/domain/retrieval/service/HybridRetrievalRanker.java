package com.aiplatform.knowledge.domain.retrieval.service;

import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.retrieval.RetrievalDocumentIdentity;
import com.aiplatform.knowledge.domain.retrieval.RetrievalEvidenceIdentity;
import com.aiplatform.knowledge.domain.retrieval.RetrievalProfile;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import com.aiplatform.knowledge.domain.retrieval.service.model.ScoredCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 使用 RRF 融合向量和关键词召回结果，并限制单文档占用上下文的数量。
 */
public class HybridRetrievalRanker {
    private static final int RRF_K = 60;
    private static final int MAX_CHUNKS_PER_DOCUMENT = 3;

    public List<RetrievalCandidate> rank(
            List<RetrievalCandidate> vectorCandidates,
            List<RetrievalCandidate> keywordCandidates,
            RetrievalProfile profile
    ) {
        Objects.requireNonNull(vectorCandidates, "向量召回列表不能为空");
        Objects.requireNonNull(keywordCandidates, "关键词召回列表不能为空");
        Objects.requireNonNull(profile, "检索画像不能为空");
        Map<RetrievalSignalIdentity, RetrievalCandidate> uniqueSignalCandidates = new LinkedHashMap<>();
        Stream.concat(vectorCandidates.stream(), keywordCandidates.stream())
                .filter(candidate -> candidate.signal() != RetrievalSignal.VECTOR || candidate.score() >= profile.scoreThreshold())
                .forEach(candidate -> uniqueSignalCandidates.merge(
                        RetrievalSignalIdentity.from(candidate),
                        candidate,
                        HybridRetrievalRanker::betterRankedCandidate
                ));

        Map<RetrievalEvidenceIdentity, ScoredCandidate> scoredCandidates = new LinkedHashMap<>();
        uniqueSignalCandidates.values().forEach(candidate -> scoredCandidates.merge(
                        candidate.evidenceIdentity(),
                        new ScoredCandidate(candidate, rrfScore(candidate.rank())),
                        ScoredCandidate::merge
                ));

        Map<RetrievalDocumentIdentity, Integer> documentCounts = new LinkedHashMap<>();
        List<RetrievalCandidate> ranked = new ArrayList<>();
        scoredCandidates.values().stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .map(ScoredCandidate::candidate)
                .forEach(candidate -> {
                    RetrievalDocumentIdentity documentIdentity = candidate.evidenceIdentity().documentIdentity();
                    int currentCount = documentCounts.getOrDefault(documentIdentity, 0);
                    if (currentCount < MAX_CHUNKS_PER_DOCUMENT && ranked.size() < profile.topK()) {
                        ranked.add(candidate);
                        documentCounts.put(documentIdentity, currentCount + 1);
                    }
                });
        return List.copyOf(ranked);
    }

    private static RetrievalCandidate betterRankedCandidate(
            RetrievalCandidate current,
            RetrievalCandidate candidate
    ) {
        if (current.rank() != candidate.rank()) {
            return current.rank() < candidate.rank() ? current : candidate;
        }
        return current.score() >= candidate.score() ? current : candidate;
    }

    private static double rrfScore(int rank) {
        return 1.0 / (RRF_K + rank);
    }
}
