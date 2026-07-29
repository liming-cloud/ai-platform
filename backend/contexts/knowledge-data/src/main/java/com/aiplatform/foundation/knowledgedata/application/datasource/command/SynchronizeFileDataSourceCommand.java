package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 触发已探测文件来源同步的可信请求。
 *
 * <p>租户、权限和对象位置均不可由调用方提交；同步只能消费已登记修订中的平台受控对象事实。</p>
 */
public record SynchronizeFileDataSourceCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String dataSourceId,
        long expectedVersion) {

    public SynchronizeFileDataSourceCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank()
                || dataSourceId == null || dataSourceId.isBlank()
                || expectedVersion < 0) {
            throw new IllegalArgumentException("文件同步请求不完整");
        }
    }
}
