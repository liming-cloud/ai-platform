package com.aiplatform.foundation.knowledgedata.application.port.out.knowledge;

import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpace;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 交给持久化适配器原子创建的知识空间请求。
 */
public record KnowledgeSpaceCreation(
        KnowledgeSpace space,
        String idempotencyKey,
        String requestFingerprint) {

    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    public KnowledgeSpaceCreation {
        Objects.requireNonNull(space, "知识空间不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
        if (!FINGERPRINT.matcher(requestFingerprint).matches()) {
            throw new IllegalArgumentException("请求指纹格式不正确");
        }
    }
}
