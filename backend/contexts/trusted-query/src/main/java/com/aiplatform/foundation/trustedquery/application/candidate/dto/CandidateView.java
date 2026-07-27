package com.aiplatform.foundation.trustedquery.application.candidate.dto;

/**
 * 已完成租户与证据边界过滤的候选视图。
 */
public record CandidateView(
        String dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal,
        String content,
        double score) {
}
