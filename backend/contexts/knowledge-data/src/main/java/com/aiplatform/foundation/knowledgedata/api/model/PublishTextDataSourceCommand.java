package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;

/**
 * 发布当前文本内容版本的命令。
 */
public record PublishTextDataSourceCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String dataSourceId,
        long expectedVersion,
        String idempotencyKey) {

    public PublishTextDataSourceCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
    }
}
