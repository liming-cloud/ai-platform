package com.aiplatform.contracts.capability.knowledge;

import java.util.List;
import java.util.Objects;

public record KnowledgeBaseAccessCheckResponse(List<KnowledgeBaseAccessCheckItem> items) {
    public KnowledgeBaseAccessCheckResponse {
        Objects.requireNonNull(items, "知识库检查结果不能为空");
        items = items.stream()
                .map(item -> Objects.requireNonNull(item, "知识库检查项不能为空"))
                .toList();
    }

    public boolean passed() {
        return !items.isEmpty() && items.stream().allMatch(KnowledgeBaseAccessCheckItem::accessible);
    }
}
