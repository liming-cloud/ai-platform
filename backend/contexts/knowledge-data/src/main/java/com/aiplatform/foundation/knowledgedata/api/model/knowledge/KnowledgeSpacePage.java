package com.aiplatform.foundation.knowledgedata.api.model.knowledge;

import java.util.List;
import java.util.Objects;

/**
 * 知识空间的游标分页结果。
 */
public record KnowledgeSpacePage(
        List<KnowledgeSpaceView> items,
        String nextCursor) {

    public KnowledgeSpacePage {
        items = List.copyOf(Objects.requireNonNull(items, "知识空间列表不能为空"));
    }
}
