package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.DisplayRole;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.WorkspaceCategory;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapabilityResult;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        if (platform.hasPlatformWorkspace()) {
            workspaces.add(WorkspaceCategory.PLATFORM_TENANT_GOVERNANCE);
            roles.addAll(platform.displayRoles());
        }
        return new BrowserSessionCapabilityResult(
                Optional.of(new BrowserSessionCapability(
                        workspaces,
                        roles,
                        Stream.concat(tenant.permissionCodes(), platform.permissionCodes())
                                .collect(Collectors.toUnmodifiableSet()),
                        maximumSessionAge.toSeconds())),
                tenant.isInvalid(),
                platform.isInvalid());
    }

    private ResolvedSession resolveTenant(String tokenValue) {
        if (tokenValue == null) {
            return ResolvedSession.absent();
        }
        try {
            return ResolvedSession.valid(tenantSessions.resolve(tokenValue));
        } catch (InvalidAuthenticatedSessionException exception) {
            return ResolvedSession.invalidSession();
        }
    }

    private ResolvedPlatformSession resolvePlatform(String tokenValue) {
        if (tokenValue == null) {
            return ResolvedPlatformSession.absent();
        }
        try {
            return ResolvedPlatformSession.valid(platformSessions.resolve(tokenValue));
        } catch (InvalidAuthenticatedSessionException exception) {
            return ResolvedPlatformSession.invalidSession();
        }
    }

    private static Duration requireMaximumSessionAge(Duration value) {
        java.util.Objects.requireNonNull(value, "最大会话年龄不能为空");
        if (value.isZero() || value.isNegative() || value.toSeconds() <= 0) {
            throw new IllegalArgumentException("最大会话年龄必须是正整数秒");
        }
        return value;
    }

    private record ResolvedSession(
            boolean isValid,
            boolean isInvalid,
            com.aiplatform.foundation.security.identity.TrustedRequestContext context) {

        static ResolvedSession absent() {
            return new ResolvedSession(false, false, null);
        }

        static ResolvedSession valid(
                com.aiplatform.foundation.security.identity.TrustedRequestContext context) {
            return new ResolvedSession(
                    true,
                    false,
                    java.util.Objects.requireNonNull(context, "租户请求上下文不能为空"));
        }

        static ResolvedSession invalidSession() {
            return new ResolvedSession(false, true, null);
        }

        Stream<String> permissionCodes() {
            return isValid ? context.permissions().stream().map(permission -> permission.value()) : Stream.empty();
        }
    }

    private record ResolvedPlatformSession(
            boolean isValid,
            boolean isInvalid,
            PlatformRequestContext context) {

        static ResolvedPlatformSession absent() {
            return new ResolvedPlatformSession(false, false, null);
        }

        static ResolvedPlatformSession valid(PlatformRequestContext context) {
            return new ResolvedPlatformSession(
                    true,
                    false,
                    java.util.Objects.requireNonNull(context, "平台请求上下文不能为空"));
        }

        static ResolvedPlatformSession invalidSession() {
            return new ResolvedPlatformSession(false, true, null);
        }

        boolean hasPlatformWorkspace() {
            return isValid && context.hasPermission(PlatformPermissions.SESSION_ACCESS);
        }

        EnumSet<DisplayRole> displayRoles() {
            var roles = EnumSet.noneOf(DisplayRole.class);
            if (!isValid) {
                return roles;
            }
            if (context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
                roles.add(DisplayRole.PLATFORM_ACCOUNT_ADMINISTRATOR);
            }
            if (context.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
                roles.add(DisplayRole.PLATFORM_TENANT_GOVERNOR);
            }
            if (roles.isEmpty()) {
                roles.add(DisplayRole.PLATFORM_OPERATOR);
            }
            return roles;
        }

        Stream<String> permissionCodes() {
            return isValid ? context.permissions().stream().map(permission -> permission.value()) : Stream.empty();
        }
    }
}
