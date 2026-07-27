package com.aiplatform.foundation.knowledgedata.application.knowledge.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * 不暴露持久化和框架类型的知识空间公共视图。
 */
public record KnowledgeSpaceView(
        String id,
        String name,
        String description,
        String status,
        long version,
        Instant createdAt) {

    public KnowledgeSpaceView {
        Objects.requireNonNull(id, "知识空间标识不能为空");
        Objects.requireNonNull(name, "知识空间名称不能为空");
        Objects.requireNonNull(status, "知识空间状态不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
    }
}
