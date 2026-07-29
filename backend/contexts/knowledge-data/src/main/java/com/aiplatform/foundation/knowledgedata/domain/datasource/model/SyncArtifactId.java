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

    public static SyncArtifactId parse(String value) {
        try {
            return new SyncArtifactId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("同步产物标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
