package com.aiplatform.foundation.knowledgedata.application.knowledge.dto;

import java.util.Objects;

/**
 * 创建结果包含资源事实和是否由幂等记录重放。
 */
public record CreateKnowledgeSpaceResult(
        KnowledgeSpaceView knowledgeSpace,
        boolean replayed) {

    public CreateKnowledgeSpaceResult {
        Objects.requireNonNull(knowledgeSpace, "知识空间不能为空");
    }
}
