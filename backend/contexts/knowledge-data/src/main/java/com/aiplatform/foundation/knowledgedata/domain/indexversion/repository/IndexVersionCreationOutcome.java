package com.aiplatform.foundation.knowledgedata.domain.indexversion.repository;

import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.IndexVersion;
import java.util.Objects;

/**
 * 索引版本创建结果及幂等重放标记。
 */
public record IndexVersionCreationOutcome(
        IndexVersion indexVersion,
        boolean replayed) {

    public IndexVersionCreationOutcome {
        Objects.requireNonNull(indexVersion, "索引版本不能为空");
    }
}
