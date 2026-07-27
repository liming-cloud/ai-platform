package com.aiplatform.foundation.knowledgedata.application.port.out.knowledge;

import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import java.util.Objects;

/**
 * 原子创建或幂等重放结果。
 */
public record KnowledgeSpaceCreationOutcome(
        KnowledgeSpace space,
        boolean replayed) {

    public KnowledgeSpaceCreationOutcome {
        Objects.requireNonNull(space, "知识空间不能为空");
    }
}
