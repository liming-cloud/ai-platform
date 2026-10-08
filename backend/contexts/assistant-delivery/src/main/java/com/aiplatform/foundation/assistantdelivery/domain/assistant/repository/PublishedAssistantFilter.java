package com.aiplatform.foundation.assistantdelivery.domain.assistant.repository;

import java.util.Objects;
import java.util.Set;

/** 仓储在分页前施加名称及明确对象范围，空授权集合不等于全部。 */
public record PublishedAssistantFilter(String q, Set<Long> assistantIds, boolean allPublished) {
    public PublishedAssistantFilter {
        q = Objects.requireNonNull(q, "搜索词不能为空").strip();
        assistantIds = Set.copyOf(assistantIds);
        if (q.length() > 160 || assistantIds.stream().anyMatch(id -> id < 1)
                || (allPublished && !assistantIds.isEmpty())) throw new IllegalArgumentException("助手目录范围不合法");
    }
}
