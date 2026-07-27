package com.aiplatform.foundation.knowledgedata.api.model.index;

import java.util.Objects;

/**
 * 索引版本发布结果。
 */
public record PublishIndexVersionResult(
        IndexVersionView indexVersion,
        boolean replayed) {

    public PublishIndexVersionResult {
        Objects.requireNonNull(indexVersion, "索引版本不能为空");
    }
}
