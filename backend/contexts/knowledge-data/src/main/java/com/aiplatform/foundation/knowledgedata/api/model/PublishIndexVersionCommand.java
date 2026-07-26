package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 把就绪索引原子发布为知识空间当前活动版本的命令。
 */
public record PublishIndexVersionCommand(
        TrustedRequestContext context,
        UUID knowledgeSpaceId,
        UUID indexVersionId,
        long expectedVersion,
        String idempotencyKey) {

    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    public PublishIndexVersionCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(indexVersionId, "索引版本标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期修订不能为负数");
        }
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
    }
}
