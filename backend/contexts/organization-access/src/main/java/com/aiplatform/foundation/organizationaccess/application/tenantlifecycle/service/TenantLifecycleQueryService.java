package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleSummary;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantDirectoryFilter;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLifecycleView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantQuotaView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantOnboardingErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台受权的租户生命周期读取用例。
 *
 * <p>授权在读取端口调用前失败关闭，避免以列表、总数或单条字段泄露租户是否存在。</p>
 */
public class TenantLifecycleQueryService {

    private final TenantLifecycleReadPort readPort;

    public TenantLifecycleQueryService(TenantLifecycleReadPort readPort) {
        this.readPort = Objects.requireNonNull(readPort, "生命周期读取端口不能为空");
    }

    /**
     * 分页读取服务端权威的最小生命周期投影。
     *
     * @throws IllegalArgumentException 当平台上下文无租户治理权限时拒绝。
     */
    @Transactional(readOnly = true)
    public PageResult<TenantLifecycleSummary> list(
            PlatformRequestContext platform,
            PageQuery page, String query, String status) {
        platform = Objects.requireNonNull(platform, "平台请求上下文不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        if (!platform.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new IllegalArgumentException("平台上下文缺少租户治理权限");
        }
        return readPort.page(page, new TenantDirectoryFilter(query, status));
    }

    /** 按平台治理权限读取单个租户，不允许借详情接口探测未授权对象。 */
    @Transactional(readOnly = true)
    public TenantLifecycleView find(PlatformRequestContext platform, long tenantId) {
        requireGovernance(platform, tenantId);
        return readPort.find(tenantId).orElseThrow(() -> new ApplicationException(TenantOnboardingErrorCode.TENANT_NOT_FOUND));
    }

    /** 独立读取配额事实；无权限优先拒绝，缺失时不编造默认值。 */
    @Transactional(readOnly = true)
    public TenantQuotaView quotas(PlatformRequestContext platform, long tenantId) {
        requireGovernance(platform, tenantId);
        return readPort.quotas(tenantId).orElseThrow(() -> new ApplicationException(TenantOnboardingErrorCode.TENANT_NOT_FOUND));
    }

    private static void requireGovernance(PlatformRequestContext platform, long tenantId) {
        if (!Objects.requireNonNull(platform, "平台请求上下文不能为空")
                .hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new ApplicationException(TenantOnboardingErrorCode.ACCESS_DENIED);
        }
        if (tenantId <= 0) {
            throw new IllegalArgumentException("租户标识格式不正确");
        }
    }
}
