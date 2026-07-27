package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 在知识空间中登记文本数据源的命令。
 */
public record CreateTextDataSourceCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String idempotencyKey,
        String name) {

    public CreateTextDataSourceCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(name, "数据源名称不能为空");
    }
}
