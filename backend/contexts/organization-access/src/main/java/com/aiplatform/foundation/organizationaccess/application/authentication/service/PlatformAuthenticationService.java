package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.organizationaccess.application.authentication.command.PlatformLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.domain.authorization.repository.PlatformAccessGrantRepository;
import com.aiplatform.foundation.organizationaccess.domain.identity.repository.SubjectIdentityRepository;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 先验证全局主体凭据，再验证独立平台授权并签发平台会话。
 */
public final class PlatformAuthenticationService {

    private final SubjectIdentityRepository identities;
    private final PlatformAccessGrantRepository grants;
    private final PasswordVerifier passwords;
    private final PlatformSessionGateway sessions;
    private final String dummyEncodedPassword;

    public PlatformAuthenticationService(
            SubjectIdentityRepository identities,
            PlatformAccessGrantRepository grants,
            PasswordVerifier passwords,
            PlatformSessionGateway sessions,
            String dummyEncodedPassword) {
        this.identities = Objects.requireNonNull(identities, "主体仓储不能为空");
        this.grants = Objects.requireNonNull(grants, "平台授权仓储不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码验证器不能为空");
        this.sessions = Objects.requireNonNull(sessions, "平台会话网关不能为空");
        this.dummyEncodedPassword = requireDummyPassword(dummyEncodedPassword);
    }

    public IssuedSession authenticate(PlatformLoginCommand command) {
        Objects.requireNonNull(command, "平台登录命令不能为空");
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
        var grant = grants.findActiveBySubject(authenticated.id());
        if (grant.isEmpty()) {
            throw new InvalidCredentialsException();
        }
        var activeGrant = grant.orElseThrow();
        var permissions = activeGrant.permissions().stream()
                .map(permission -> new PermissionCode(permission.value()))
                .collect(Collectors.toUnmodifiableSet());
        return sessions.issue(new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(
                        authenticated.id().value()),
                CredentialType.PERSON,
                command.channel(),
                activeGrant.permissionVersion(),
                permissions,
                command.traceId()));
    }

    private static String requireDummyPassword(String value) {
        Objects.requireNonNull(value, "等价验证摘要不能为空");
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("等价验证摘要格式不正确");
        }
        return value;
    }
}
