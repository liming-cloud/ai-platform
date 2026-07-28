package com.aiplatform.foundation.modelruntime.application.configuration.dto;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 异步任务按创建成员重新校验受控模型配置使用权所需的最小归属事实。
 *
 * <p>该对象只能由已持久化业务记录中的租户和创建成员生成，不能映射外部请求字段。</p>
 */
public record ModelConfigurationUsageContext(
        TenantId tenantId,
        ModelConfigurationOwnerId membershipId) {

    public ModelConfigurationUsageContext {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(membershipId, "成员标识不能为空");
    }
}
