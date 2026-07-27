package com.aiplatform.foundation.knowledgedata.application.port.out.knowledge;

import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import java.util.List;
import java.util.Objects;

/**
 * 持久化端口返回的稳定游标切片。
 */
public record KnowledgeSpaceSlice(
        List<KnowledgeSpace> items,
        String nextCursor) {

    public KnowledgeSpaceSlice {
        items = List.copyOf(Objects.requireNonNull(items, "知识空间列表不能为空"));
    }
}
