package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/** 触发已登记结构化数据集修订同步的可信请求。 */
public record SynchronizeStructuredDatasetCommand(
        TrustedRequestContext context, String knowledgeSpaceId, String dataSourceId, long expectedVersion) {
    public SynchronizeStructuredDatasetCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank() || dataSourceId == null || dataSourceId.isBlank() || expectedVersion < 0) {
            throw new IllegalArgumentException("结构化数据集同步请求不完整");
        }
    }
}
