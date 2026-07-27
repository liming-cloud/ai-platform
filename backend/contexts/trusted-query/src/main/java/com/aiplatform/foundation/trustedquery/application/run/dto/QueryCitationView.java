package com.aiplatform.foundation.trustedquery.application.run.dto;

/**
 * 最终用户可核验的回答引用。
 */
public record QueryCitationView(
        int sequence,
        String dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal,
        String quote,
        double score) {
}
