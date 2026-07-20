package com.aiplatform.infra.vector.model;

/**
 * 向量库返回的候选片段结果，作为基础设施层与检索端口之间的数据载体。
 */
public final class VectorSearchResult {
    private final String chunkId;
    private final String documentId;
    private final String content;
    private final double score;
    private final String sourceTitle;

    public VectorSearchResult(String chunkId, String documentId, String content, double score, String sourceTitle) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.content = content;
        this.score = score;
        this.sourceTitle = sourceTitle;
    }

    public String chunkId() { return chunkId; }
    public String documentId() { return documentId; }
    public String content() { return content; }
    public double score() { return score; }
    public String sourceTitle() { return sourceTitle; }
}
