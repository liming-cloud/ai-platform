package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.DisplayRole;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.WorkspaceCategory;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapabilityResult;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;

/**
 * 把已验证的浏览器范围会话投影为导航能力，不把上下文事实暴露给浏览器。
 */
public final class BrowserSessionCapabilityService {

    private final AuthenticatedSessionGateway tenantSessions;
    private final PlatformSessionGateway platformSessions;
    private final Duration maximumSessionAge;

    public BrowserSessionCapabilityService(
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions,
            Duration maximumSessionAge) {
        this.tenantSessions = java.util.Objects.requireNonNull(tenantSessions, "租户会话网关不能为空");
        this.platformSessions = java.util.Objects.requireNonNull(platformSessions, "平台会话网关不能为空");
        this.maximumSessionAge = requireMaximumSessionAge(maximumSessionAge);
    }

    /**
     * 分别恢复两个范围会话；失效范围只影响自身，不降级为另一范围身份。
     */
    public BrowserSessionCapabilityResult inspect(
            String tenantSessionToken,
            String platformSessionToken) {
        var tenant = resolveTenant(tenantSessionToken);
        var platform = resolvePlatform(platformSessionToken);
        if (!tenant.isValid() && !platform.isValid()) {
            return new BrowserSessionCapabilityResult(
                    Optional.empty(), tenant.isInvalid(), platform.isInvalid());
        }
        var workspaces = EnumSet.noneOf(WorkspaceCategory.class);
        var roles = EnumSet.noneOf(DisplayRole.class);
        if (tenant.isValid()) {
            workspaces.add(WorkspaceCategory.TENANT);
            roles.add(DisplayRole.TENANT_MEMBER);
        }
        if (platform.isValid()) {
            workspaces.add(WorkspaceCategory.PLATFORM_TENANT_GOVERNANCE);
            roles.add(DisplayRole.PLATFORM_TENANT_GOVERNOR);
        }
        return new BrowserSessionCapabilityResult(
                Optional.of(new BrowserSessionCapability(workspaces, roles, maximumSessionAge.toSeconds())),
                tenant.isInvalid(),
                platform.isInvalid());
    }

    private ResolvedSession resolveTenant(String tokenValue) {
        if (tokenValue == null) {
            return ResolvedSession.absent();
        }
        try {
            tenantSessions.resolve(tokenValue);
            return ResolvedSession.valid();
        } catch (InvalidAuthenticatedSessionException exception) {
            return ResolvedSession.invalidSession();
        }
    }

    private ResolvedSession resolvePlatform(String tokenValue) {
        if (tokenValue == null) {
            return ResolvedSession.absent();
        }
        try {
            platformSessions.resolve(tokenValue);
            return ResolvedSession.valid();
        } catch (InvalidAuthenticatedSessionException exception) {
            return ResolvedSession.invalidSession();
        }
    }

    private static Duration requireMaximumSessionAge(Duration value) {
        java.util.Objects.requireNonNull(value, "最大会话年龄不能为空");
        if (value.isZero() || value.isNegative() || value.toSeconds() <= 0) {
            throw new IllegalArgumentException("最大会话年龄必须是正整数秒");
        }
        return value;
    }

    private record ResolvedSession(boolean isValid, boolean isInvalid) {

        static ResolvedSession absent() {
            return new ResolvedSession(false, false);
        }

        static ResolvedSession valid() {
            return new ResolvedSession(true, false);
        }

        static ResolvedSession invalidSession() {
            return new ResolvedSession(false, true);
        }
    }
}
