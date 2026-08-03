package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantLifecycleTransitionCommand;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleTransitionResult;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleAction;
import com.aiplatform.foundation.organizationaccess.domain.tenant.service.TenantLifecycleTransition;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台受权的租户生命周期迁移用例。
 */
public class TenantLifecycleTransitionService {

    private final TenantLifecycleTransitionStore store;
    private final Clock clock;

    public TenantLifecycleTransitionService(
            TenantLifecycleTransitionStore store,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "生命周期迁移存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 在受权平台上下文内迁移指定租户。
     *
     * @throws IllegalArgumentException 当平台上下文无权限或租户不存在时拒绝。
     */
    @Transactional
    public TenantLifecycleTransitionResult transition(
            PlatformRequestContext platform,
            TenantLifecycleTransitionCommand command) {
        platform = Objects.requireNonNull(platform, "平台请求上下文不能为空");
        command = Objects.requireNonNull(command, "生命周期迁移命令不能为空");
        if (!platform.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new IllegalArgumentException("平台上下文缺少租户治理权限");
        }
        var lifecycle = store.lock(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("租户生命周期不存在"));
        var result = TenantLifecycleTransition.transition(lifecycle, command.action(), clock.instant());
        store.commit(result);
        return result;
    }

    /**
     * 激活路径指定的准备中租户，并返回接口可消费的读取视图。
     */
    public TenantLifecycleView activate(PlatformRequestContext platform, String tenantId) {
        return transitionView(platform, tenantId, TenantLifecycleAction.ACTIVATE);
    }

    /**
     * 挂起路径指定的活跃租户，并返回接口可消费的读取视图。
     */
    public TenantLifecycleView suspend(PlatformRequestContext platform, String tenantId) {
        return transitionView(platform, tenantId, TenantLifecycleAction.SUSPEND);
    }

    /**
     * 恢复路径指定的已挂起租户，并返回接口可消费的读取视图。
     */
    public TenantLifecycleView restore(PlatformRequestContext platform, String tenantId) {
        return transitionView(platform, tenantId, TenantLifecycleAction.RESTORE);
    }

    private TenantLifecycleView transitionView(
            PlatformRequestContext platform,
            String tenantId,
            TenantLifecycleAction action) {
        var result = transition(
                platform,
                new TenantLifecycleTransitionCommand(TenantId.parse(tenantId), action));
        return TenantLifecycleView.from(result.lifecycle());
    }
}
