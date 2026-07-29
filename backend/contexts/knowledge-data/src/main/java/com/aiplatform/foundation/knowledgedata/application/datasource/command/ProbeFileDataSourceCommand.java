package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 触发已登记文件来源对象核验的可信请求。
 *
 * <p>租户只能由服务端可信上下文提供，调用方不得提交对象键、桶名或任何连接参数。</p>
 */
public record ProbeFileDataSourceCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String dataSourceId,
        long expectedVersion) {

    public ProbeFileDataSourceCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank()
                || dataSourceId == null || dataSourceId.isBlank()
                || expectedVersion < 0) {
            throw new IllegalArgumentException("文件探测请求不完整");
        }
    }
}
