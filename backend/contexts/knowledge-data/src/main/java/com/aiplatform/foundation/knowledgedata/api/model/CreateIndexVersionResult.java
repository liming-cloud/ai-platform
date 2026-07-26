package com.aiplatform.foundation.knowledgedata.api.model;

import java.util.Objects;

/**
 * 索引版本异步创建结果。
 */
public record CreateIndexVersionResult(
        IndexVersionView indexVersion,
        boolean replayed) {

    public CreateIndexVersionResult {
        Objects.requireNonNull(indexVersion, "索引版本不能为空");
    }
}
