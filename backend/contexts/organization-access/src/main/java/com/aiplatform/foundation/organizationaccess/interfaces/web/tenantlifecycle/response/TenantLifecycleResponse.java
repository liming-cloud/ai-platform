package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import java.util.Objects;

/**
 * 租户生命周期的最小公开状态投影。
 *
 * <p>不包含当前平台操作者、凭据、成员详情或发件箱内容。</p>
 */
public record TenantLifecycleResponse(
        String tenantId,
        String tenantCode,
        String displayName,
        String initialAdministratorSubjectId,
        String status,
        long version) {

    /**
     * 从领域根投影为稳定 HTTP 响应。
     */
    public static TenantLifecycleResponse from(TenantLifecycleView lifecycle) {
        lifecycle = Objects.requireNonNull(lifecycle, "租户生命周期视图不能为空");
        return new TenantLifecycleResponse(
                Long.toString(lifecycle.tenantId()),
                lifecycle.tenantCode(),
                lifecycle.displayName(),
                Long.toString(lifecycle.initialAdministratorSubjectId()),
                lifecycle.status(),
                lifecycle.version());
    }
}
