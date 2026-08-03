package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import java.util.Objects;

/**
 * 供接口层消费的租户生命周期最小读取视图。
 */
public record TenantLifecycleView(
        long tenantId,
        String tenantCode,
        String displayName,
        long initialAdministratorSubjectId,
        String status,
        long version) {

    /**
     * 从已验证领域根建立只读应用视图。
     */
    public static TenantLifecycleView from(TenantLifecycle lifecycle) {
        lifecycle = Objects.requireNonNull(lifecycle, "租户生命周期不能为空");
        return new TenantLifecycleView(
                lifecycle.id().value(),
                lifecycle.code(),
                lifecycle.displayName(),
                lifecycle.initialAdministratorId().value(),
                lifecycle.status().name(),
                lifecycle.version());
    }
}
