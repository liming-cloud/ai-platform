package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 同步产物的稳定标识。
 */
public record SyncArtifactId(long value) implements ValueObject {

    public SyncArtifactId {
        if (value <= 0) {
            throw new IllegalArgumentException("同步产物标识必须是正整数");
        }
    }
}
