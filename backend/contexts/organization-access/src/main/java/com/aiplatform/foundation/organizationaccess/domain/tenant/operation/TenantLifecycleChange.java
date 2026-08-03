package com.aiplatform.foundation.organizationaccess.domain.tenant.operation;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * 租户生命周期一次已验证状态迁移的最小领域事实。
 *
 * <p>该事实只携带发件箱所需的目标租户、状态、修订与发生时间；初始管理员、成员详情、正文与凭据
 * 不得进入该事实。</p>
 */
public record TenantLifecycleChange(
        TenantId tenantId,
        TenantLifecycleStatus status,
        long version,
        Instant occurredAt) implements ValueObject {

    public TenantLifecycleChange {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        status = Objects.requireNonNull(status, "租户生命周期状态不能为空");
        if (version < 0L) {
            throw new IllegalArgumentException("租户生命周期修订不能为负数");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "生命周期发生时间不能为空");
    }
}
