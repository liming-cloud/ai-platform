package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 在可信租户上下文中创建知识空间的命令。
 */
public record CreateKnowledgeSpaceCommand(
        TrustedRequestContext context,
        String idempotencyKey,
        String name,
        String description) {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    public CreateKnowledgeSpaceCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
    }
}
