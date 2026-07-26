package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import java.util.Objects;

/**
 * 交给持久化适配器原子创建的知识空间请求。
 */
public record KnowledgeSpaceCreation(
        KnowledgeSpace space,
        String idempotencyKey,
        String requestFingerprint) {

    public KnowledgeSpaceCreation {
        Objects.requireNonNull(space, "知识空间不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
    }
}
