package com.aiplatform.foundation.knowledgedata.domain.indexversion;

import java.util.Objects;
import java.util.UUID;

/**
 * 索引版本标识。
 */
public record IndexVersionId(UUID value) {

    public IndexVersionId {
        Objects.requireNonNull(value, "索引版本标识不能为空");
    }
}
