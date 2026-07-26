package com.aiplatform.foundation.knowledgedata.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 数据源的稳定标识。
 */
public record DataSourceId(UUID value) {

    public DataSourceId {
        Objects.requireNonNull(value, "数据源标识不能为空");
    }
}
