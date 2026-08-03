package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.command.BrowserLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionIssue;
import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMembershipRepository;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 统一浏览器身份验证并按已验证角色签发彼此不可互用的范围会话。
 */
public final class BrowserAuthenticationService {

    private final SubjectIdentityRepository identities;
    private final TenantMembershipRepository memberships;
    private final PlatformAccessGrantRepository grants;
    private final PasswordVerifier passwords;
    private final AuthenticatedSessionGateway tenantSessions;
    private final PlatformSessionGateway platformSessions;
    private final String dummyEncodedPassword;

    public BrowserAuthenticationService(
            SubjectIdentityRepository identities,
            TenantMembershipRepository memberships,
            PlatformAccessGrantRepository grants,
            PasswordVerifier passwords,
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions,
            String dummyEncodedPassword) {
        this.identities = java.util.Objects.requireNonNull(identities, "主体仓储不能为空");
        this.memberships = java.util.Objects.requireNonNull(memberships, "成员资格仓储不能为空");
        this.grants = java.util.Objects.requireNonNull(grants, "平台授权仓储不能为空");
        this.passwords = java.util.Objects.requireNonNull(passwords, "密码验证器不能为空");
        this.tenantSessions = java.util.Objects.requireNonNull(tenantSessions, "租户会话网关不能为空");
        this.platformSessions = java.util.Objects.requireNonNull(platformSessions, "平台会话网关不能为空");
        this.dummyEncodedPassword = requireDummyPassword(dummyEncodedPassword);
    }

    /**
     * 只校验一次主体口令，随后独立判断已请求租户成员资格与平台治理授权。
     *
     * @throws InvalidCredentialsException 当主体、口令、显式租户目标或全部角色均无效时拒绝。
     */
    public BrowserSessionIssue authenticate(BrowserLoginCommand command) {
        java.util.Objects.requireNonNull(command, "浏览器登录命令不能为空");
        var identity = identities.findActiveByLogin(command.loginName());
        var encodedPassword = identity
                .map(subject -> subject.encodedPassword().verificationValue())
                .orElse(dummyEncodedPassword);
        var candidate = command.copyPassword();
        boolean matched;
        try {
            matched = passwords.matches(candidate, encodedPassword);
        } finally {
            Arrays.fill(candidate, '\0');
        }
        if (identity.isEmpty() || !matched) {
            throw new InvalidCredentialsException();
        }
        var authenticated = identity.orElseThrow();
        var member = command.tenantCode() == null
                ? Optional.<com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile>empty()
                : memberships.findEnabled(command.tenantCode(), authenticated.id());
        if (command.tenantCode() != null && member.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        var grant = grants.findActiveBySubject(authenticated.id());
        if (member.isEmpty() && grant.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        IssuedSession tenantSession = null;
        try {
            if (member.isPresent()) {
                var current = member.orElseThrow();
                var permissions = current.permissions().stream()
                        .map(permission -> new PermissionCode(permission.value()))
                        .collect(Collectors.toUnmodifiableSet());
                tenantSession = tenantSessions.issue(new TrustedRequestContext(
                        current.tenantId(),
                        new SubjectId(current.subjectId().value()),
                        new MembershipId(current.membershipId().value()),
                        CredentialType.PERSON,
                        command.channel(),
                        current.permissionVersion(),
                        permissions,
                        command.traceId()));
            }
            if (grant.isPresent()) {
                var current = grant.orElseThrow();
                var permissions = current.permissions().stream()
                        .map(permission -> new PermissionCode(permission.value()))
                        .collect(Collectors.toUnmodifiableSet());
                var platformSession = platformSessions.issue(new PlatformRequestContext(
                        new SubjectId(authenticated.id().value()),
                        CredentialType.PERSON,
                        command.channel(),
                        current.permissionVersion(),
                        permissions,
                        command.traceId()));
                return new BrowserSessionIssue(Optional.ofNullable(tenantSession), Optional.of(platformSession));
            }
            return new BrowserSessionIssue(Optional.ofNullable(tenantSession), Optional.empty());
        } catch (RuntimeException exception) {
            if (tenantSession != null) {
                revokeTenantAfterFailedIssue(tenantSession.tokenValue(), exception);
            }
            throw exception;
        }
    }

    private void revokeTenantAfterFailedIssue(String tokenValue, RuntimeException original) {
        try {
            tenantSessions.revoke(tokenValue);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private static String requireDummyPassword(String value) {
        java.util.Objects.requireNonNull(value, "等价验证摘要不能为空");
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("等价验证摘要格式不正确");
        }
        return value;
    }
}
