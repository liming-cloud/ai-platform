package com.aiplatform.foundation.knowledgedata.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 知识空间的稳定标识。
 */
public record KnowledgeSpaceId(UUID value) {

    public KnowledgeSpaceId {
        Objects.requireNonNull(value, "知识空间标识不能为空");
    }
}
