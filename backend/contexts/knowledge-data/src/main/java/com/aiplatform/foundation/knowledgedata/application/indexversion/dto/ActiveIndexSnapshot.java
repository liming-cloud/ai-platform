package com.aiplatform.foundation.knowledgedata.application.indexversion.dto;

import com.aiplatform.foundation.knowledgedata.domain.indexversion.model.EmbeddingConfiguration;
import java.util.Objects;

/**
 * 助手版本可以冻结的活动索引及嵌入绑定事实。
 */
public record ActiveIndexSnapshot(
        long knowledgeSpaceId,
        long indexVersionId,
        long indexVersionRevision,
        EmbeddingConfiguration embedding) {

    public ActiveIndexSnapshot {
        if (knowledgeSpaceId <= 0 || indexVersionId <= 0) {
            throw new IllegalArgumentException("知识空间标识和索引版本标识必须是正整数");
        }
        embedding = Objects.requireNonNull(embedding, "嵌入绑定不能为空");
        if (indexVersionRevision < 1) {
            throw new IllegalArgumentException("索引修订必须大于零");
        }
    }
}
