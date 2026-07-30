package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/** 知识管理员触发对象存储来源范围一致性探测的可信请求。 */
public record ProbeObjectStorageDataSourceCommand(
        TrustedRequestContext context, String knowledgeSpaceId, String dataSourceId, long expectedVersion) {
    public ProbeObjectStorageDataSourceCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank() || dataSourceId == null || dataSourceId.isBlank() || expectedVersion < 0) throw new IllegalArgumentException("对象存储探测请求不完整");
    }
}
