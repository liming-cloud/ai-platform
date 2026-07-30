package com.aiplatform.foundation.knowledgedata.application.datasource.command.synchronization;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/** 触发已登记对象存储修订同步的可信请求。 */
public record SynchronizeObjectStorageDataSourceCommand(
        TrustedRequestContext context, String knowledgeSpaceId, String dataSourceId, long expectedVersion) {
    public SynchronizeObjectStorageDataSourceCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank()
                || dataSourceId == null || dataSourceId.isBlank() || expectedVersion < 0) {
            throw new IllegalArgumentException("对象存储同步请求不完整");
        }
    }
}
