package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.member.service.MemberAccountApplicationService;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantWorkspaceContextView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleReadPort;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantOnboardingErrorCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 先复核有效管理员资格，再投影令牌所属租户名称；不能用于选择其他租户。 */
public class TenantWorkspaceContextQueryService {
    private final MemberAccountApplicationService accounts;
    private final TenantLifecycleReadPort tenants;

    /** 复用现有管理员事实和租户读取端口，不建立另一套身份或名称来源。 */
    public TenantWorkspaceContextQueryService(MemberAccountApplicationService accounts, TenantLifecycleReadPort tenants) {
        this.accounts = Objects.requireNonNull(accounts);
        this.tenants = Objects.requireNonNull(tenants);
    }

    /** 当前资格失效时先拒绝，租户事实缺失时不返回演示值或其他租户数据。 */
    @Transactional(readOnly = true)
    public TenantWorkspaceContextView get(TrustedRequestContext context) {
        accounts.requireAdministrator(context);
        var tenant = tenants.find(context.tenantId().value())
                .orElseThrow(() -> new ApplicationException(TenantOnboardingErrorCode.TENANT_NOT_FOUND));
        return new TenantWorkspaceContextView(tenant.displayName());
    }
}
