package com.aiplatform.knowledge.domain.retrieval;

/**
 * 检索画像固定平台默认阈值，避免每个入口随意传 topK 和相似度造成质量不可控。
 */
public enum RetrievalProfile {
    STRICT(5, 0.75, false),
    BALANCED(10, 0.65, false),
    EXPLORATORY(20, 0.55, true);

    private final int topK;
    private final double scoreThreshold;
    private final boolean lowConfidenceAllowed;

    RetrievalProfile(int topK, double scoreThreshold, boolean lowConfidenceAllowed) {
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;
        this.lowConfidenceAllowed = lowConfidenceAllowed;
    }

    public int topK() {
        return topK;
    }

    public double scoreThreshold() {
        return scoreThreshold;
    }

    public boolean lowConfidenceAllowed() {
        return lowConfidenceAllowed;
    }
}
