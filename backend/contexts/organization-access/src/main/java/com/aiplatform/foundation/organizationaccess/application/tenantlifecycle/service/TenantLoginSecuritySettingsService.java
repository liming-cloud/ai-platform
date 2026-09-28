package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLoginSecurityPolicyResult;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantLoginSecurityErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception.TenantOnboardingErrorCode;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLoginSecurityPolicyStore;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantSessionRevoker;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLoginSecurityPolicy;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 平台租户安全策略的独立读写用例；有变化时同步撤销目标租户旧会话。 */
public class TenantLoginSecuritySettingsService {
    private final TenantLoginSecurityPolicyStore store;
    private final TenantSessionRevoker sessions;

    public TenantLoginSecuritySettingsService(TenantLoginSecurityPolicyStore store, TenantSessionRevoker sessions) {
        this.store = Objects.requireNonNull(store, "登录策略存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "租户会话撤销器不能为空");
    }

    /** 只向有平台治理权限的会话展示目标租户的策略。 */
    @Transactional(readOnly = true)
    public TenantLoginSecurityPolicyResult get(PlatformRequestContext platform, long tenantId) {
        requireGovernance(platform, tenantId);
        return TenantLoginSecurityPolicyResult.from(requireExisting(tenantId));
    }

    /** 以预期版本完整替换三项值；撤销失败使数据库事务回滚。 */
    @Transactional
    public TenantLoginSecurityPolicyResult update(PlatformRequestContext platform, long tenantId,
            int sessionHours, int failedAttempts, int lockMinutes, long expectedVersion) {
        requireGovernance(platform, tenantId);
        if (expectedVersion < 0) throw new IllegalArgumentException("预期版本不能为负数");
        var current = requireExisting(tenantId);
        if (current.version() != expectedVersion) {
            throw new ApplicationException(TenantLoginSecurityErrorCode.VERSION_CONFLICT);
        }
        var checked = new TenantLoginSecurityPolicy(current.tenantId(), sessionHours,
                failedAttempts, lockMinutes, current.version());
        if (current.sessionHours() == checked.sessionHours()
                && current.failedAttempts() == checked.failedAttempts()
                && current.lockMinutes() == checked.lockMinutes()) {
            return TenantLoginSecurityPolicyResult.from(current);
        }
        var next = new TenantLoginSecurityPolicy(current.tenantId(), sessionHours,
                failedAttempts, lockMinutes, current.version() + 1);
        if (!store.replace(next, current.version())) {
            throw new ApplicationException(TenantLoginSecurityErrorCode.VERSION_CONFLICT);
        }
        sessions.revokeTenantSessions(next.tenantId());
        return TenantLoginSecurityPolicyResult.from(next);
    }

    private TenantLoginSecurityPolicy requireExisting(long tenantId) {
        return store.find(tenantId)
                .orElseThrow(() -> new ApplicationException(TenantOnboardingErrorCode.TENANT_NOT_FOUND));
    }

    private static void requireGovernance(PlatformRequestContext platform, long tenantId) {
        if (!Objects.requireNonNull(platform, "平台请求上下文不能为空")
                .hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new ApplicationException(TenantOnboardingErrorCode.ACCESS_DENIED);
        }
        if (tenantId <= 0) throw new IllegalArgumentException("租户标识格式不正确");
    }
}
