package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.DisplayRole;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapability.WorkspaceCategory;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapabilityResult;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantFeatureSettingsStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeature;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantFeatureSettings;
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
    private final TenantFeatureSettingsStore featureSettings;

    public BrowserSessionCapabilityService(
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions,
            Duration maximumSessionAge,
            TenantFeatureSettingsStore featureSettings) {
        this.tenantSessions = java.util.Objects.requireNonNull(tenantSessions, "租户会话网关不能为空");
        this.platformSessions = java.util.Objects.requireNonNull(platformSessions, "平台会话网关不能为空");
        this.maximumSessionAge = requireMaximumSessionAge(maximumSessionAge);
        this.featureSettings = java.util.Objects.requireNonNull(featureSettings, "租户功能配置存储不能为空");
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
            roles.add(tenant.isAdministrator()
                    ? DisplayRole.TENANT_ADMINISTRATOR
                    : DisplayRole.TENANT_USER);
        }
        if (platform.hasPlatformWorkspace()) {
            workspaces.add(WorkspaceCategory.PLATFORM_TENANT_GOVERNANCE);
            roles.addAll(platform.displayRoles());
        }
        var tenantFeatures = tenant.isValid()
                ? featureSettings.find(tenant.context.tenantId().value())
                        .orElseThrow(() -> new IllegalStateException("租户功能配置缺失"))
                : null;
        return new BrowserSessionCapabilityResult(
                Optional.of(new BrowserSessionCapability(
                        workspaces,
                        roles,
                        Stream.concat(tenant.permissionCodes().filter(code -> allowsNavigation(tenantFeatures, code)), platform.permissionCodes())
                                .collect(Collectors.toUnmodifiableSet()),
                        maximumSessionAge.toSeconds())),
                tenant.isInvalid(),
                platform.isInvalid());
    }

    private static boolean allowsNavigation(TenantFeatureSettings settings, String code) {
        return switch (code) {
            case "knowledge:read", "knowledge:write" -> settings.allows(TenantFeature.KNOWLEDGE_MANAGEMENT);
            case "model:read", "model:write" -> settings.allows(TenantFeature.MODEL_GOVERNANCE);
            case "assistant:read", "assistant:write" -> settings.allows(TenantFeature.ASSISTANT_DELIVERY);
            case "assistant:query" -> settings.allows(TenantFeature.ASSISTANT_SELECTION)
                    && settings.allows(TenantFeature.CONVERSATION);
            default -> true;
        };
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

        boolean isAdministrator() {
            return isValid && context.permissions().stream()
                    .anyMatch(permission -> "access:manage".equals(permission.value()));
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
            if (hasPlatformWorkspace()) {
                roles.add(DisplayRole.PLATFORM_ADMINISTRATOR);
            }
            return roles;
        }

        Stream<String> permissionCodes() {
            return isValid ? context.permissions().stream().map(permission -> permission.value()) : Stream.empty();
        }
    }
}
