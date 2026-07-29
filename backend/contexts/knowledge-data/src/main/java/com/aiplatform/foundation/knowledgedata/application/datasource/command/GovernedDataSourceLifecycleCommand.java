package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 对指定受治理来源执行带预期修订的生命周期动作。
 *
 * <p>租户只来自可信上下文，调用方不能通过此命令指定租户或伪造同步产物。</p>
 */
public record GovernedDataSourceLifecycleCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String dataSourceId,
        long expectedVersion,
        GovernedDataSourceLifecycleAction action) {

    public GovernedDataSourceLifecycleCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        Objects.requireNonNull(action, "来源生命周期动作不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期来源修订不能为负数");
        }
    }
}
