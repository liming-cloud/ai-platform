package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 从知识空间当前已发布内容创建不可变索引版本的命令。
 */
public record CreateIndexVersionCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String embeddingDeploymentId,
        String idempotencyKey) {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    public CreateIndexVersionCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(embeddingDeploymentId, "嵌入部署标识不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
    }
}
