package com.aiplatform.knowledge.domain.indexing;

import java.util.Locale;
import java.util.Objects;

public enum KnowledgeIndexRebuildStatus {
    ACTIVE,
    COMPLETED,
    FAILED;

    public static KnowledgeIndexRebuildStatus from(String value) {
        Objects.requireNonNull(value, "索引重建状态不能为空");
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
