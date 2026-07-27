package com.aiplatform.foundation.trustedquery.application.run.service;

import com.aiplatform.foundation.knowledgedata.application.indexversion.port.out.vector.IndexCandidate;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 在已经完成租户和索引授权过滤的候选池内执行确定性混合重排。
 *
 * <p>该选择器不访问外部资源，也不能扩大上游已经确定的授权范围。</p>
 */
final class QueryEvidenceSelector {

    private static final int CANDIDATE_MULTIPLIER = 4;
    private static final int MAX_CANDIDATE_LIMIT = 100;
    private static final double VECTOR_WEIGHT = 0.55;
    private static final double LEXICAL_WEIGHT = 0.45;

    private QueryEvidenceSelector() {
    }

    static int candidateLimit(int resultLimit) {
        requireResultLimit(resultLimit);
        return Math.min(
                MAX_CANDIDATE_LIMIT,
                Math.multiplyExact(resultLimit, CANDIDATE_MULTIPLIER));
    }

    static List<IndexCandidate> select(
            String question,
            List<IndexCandidate> candidates,
            int resultLimit,
            double minimumScore) {
        Objects.requireNonNull(question, "查询问题不能为空");
        if (question.isBlank()) {
            throw new IllegalArgumentException("查询问题不能为空白");
        }
        Objects.requireNonNull(candidates, "检索候选不能为空");
        requireResultLimit(resultLimit);
        if (!Double.isFinite(minimumScore)
                || minimumScore < 0
                || minimumScore > 1) {
            throw new IllegalArgumentException("最低相关性必须在 0 到 1 之间");
        }
        var queryFragments = fragments(question);
        var scored = new ArrayList<ScoredCandidate>(candidates.size());
        for (var index = 0; index < candidates.size(); index++) {
            var candidate = Objects.requireNonNull(
                    candidates.get(index),
                    "检索候选项不能为空");
            var score = relevance(queryFragments, candidate);
            if (score >= minimumScore) {
                scored.add(new ScoredCandidate(candidate, score, index));
            }
        }
        return scored.stream()
                .sorted(Comparator
                        .comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparingInt(ScoredCandidate::originalIndex))
                .limit(resultLimit)
                .map(ScoredCandidate::toCandidate)
                .toList();
    }

    private static double relevance(
            Set<String> queryFragments,
            IndexCandidate candidate) {
        var vectorScore = (candidate.score() + 1.0) / 2.0;
        var candidateFragments = fragments(candidate.content());
        var matched = queryFragments.stream()
                .filter(candidateFragments::contains)
                .count();
        var lexicalScore = (double) matched / queryFragments.size();
        return Math.clamp(
                vectorScore * VECTOR_WEIGHT + lexicalScore * LEXICAL_WEIGHT,
                0.0,
                1.0);
    }

    private static Set<String> fragments(String value) {
        var codePoints = Normalizer
                .normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .codePoints()
                .filter(Character::isLetterOrDigit)
                .toArray();
        if (codePoints.length == 0) {
            throw new IllegalArgumentException("文本没有可比较内容");
        }
        var result = new HashSet<String>();
        if (codePoints.length == 1) {
            result.add(new String(codePoints, 0, 1));
            return Set.copyOf(result);
        }
        for (var index = 0; index + 1 < codePoints.length; index++) {
            result.add(new String(codePoints, index, 2));
        }
        return Set.copyOf(result);
    }

    private static void requireResultLimit(int resultLimit) {
        if (resultLimit < 1 || resultLimit > MAX_CANDIDATE_LIMIT) {
            throw new IllegalArgumentException("最终证据数量必须在 1 到 100 之间");
        }
    }

    private record ScoredCandidate(
            IndexCandidate candidate,
            double score,
            int originalIndex) {

        private IndexCandidate toCandidate() {
            return new IndexCandidate(
                    candidate.dataSourceId(),
                    candidate.contentVersion(),
                    candidate.chunkId(),
                    candidate.ordinal(),
                    candidate.content(),
                    score);
        }
    }
}
