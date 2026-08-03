package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleAction;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleChange;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleTransitionResult;
import java.time.Instant;
import java.util.Objects;

/**
 * 将一个已加载租户生命周期依法迁移并冻结最小状态事实的无状态领域服务。
 */
public final class TenantLifecycleTransition {

    private TenantLifecycleTransition() {
    }

    /**
     * 执行一个显式生命周期操作。
     *
     * @throws IllegalStateException 当当前状态不允许所选操作时拒绝。
     */
    public static TenantLifecycleTransitionResult transition(
            TenantLifecycle lifecycle,
            TenantLifecycleAction action,
            Instant occurredAt) {
        lifecycle = Objects.requireNonNull(lifecycle, "租户生命周期不能为空");
        action = Objects.requireNonNull(action, "租户生命周期操作不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        var transitioned = switch (action) {
            case ACTIVATE -> lifecycle.activate();
            case SUSPEND -> lifecycle.suspend();
            case RESTORE -> lifecycle.restore();
        };
        return new TenantLifecycleTransitionResult(
                transitioned,
                new TenantLifecycleChange(
                        transitioned.id(), transitioned.status(), transitioned.version(), occurredAt));
    }
}
