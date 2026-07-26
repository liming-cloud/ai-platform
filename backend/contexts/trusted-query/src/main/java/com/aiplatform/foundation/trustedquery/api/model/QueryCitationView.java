package com.aiplatform.foundation.trustedquery.api.model;

import java.util.UUID;

/**
 * 最终用户可核验的回答引用。
 */
public record QueryCitationView(
        int sequence,
        UUID dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal,
        String quote,
        double score) {
}
