package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.organizationaccess.application.authentication.command.PersonLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.exception.InvalidCredentialsException;
import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberAuthenticationProfile;
import com.aiplatform.foundation.organizationaccess.domain.member.repository.TenantMemberCredentialRepository;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 使用持久化成员事实完成认证并签发可信会话。
 */
public final class PersonAuthenticationService {

    private final TenantMemberCredentialRepository members;
    private final PasswordVerifier passwords;
    private final AuthenticatedSessionGateway sessions;
    private final String dummyEncodedPassword;

    public PersonAuthenticationService(
            TenantMemberCredentialRepository members,
            PasswordVerifier passwords,
            AuthenticatedSessionGateway sessions,
            String dummyEncodedPassword) {
        this.members = Objects.requireNonNull(members, "成员仓储不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码验证器不能为空");
        this.sessions = Objects.requireNonNull(sessions, "会话网关不能为空");
        this.dummyEncodedPassword = requireDummyPassword(dummyEncodedPassword);
    }

    public IssuedSession authenticate(PersonLoginCommand command) {
        Objects.requireNonNull(command, "登录命令不能为空");
        var member = members.findEnabled(command.tenantCode(), command.loginName());
        var encodedPassword = member
                .map(MemberAuthenticationProfile::encodedPassword)
                .map(password -> password.verificationValue())
                .orElse(dummyEncodedPassword);
        var candidate = command.copyPassword();
        boolean matched;
        try {
            matched = passwords.matches(candidate, encodedPassword);
        } finally {
            Arrays.fill(candidate, '\0');
        }
        if (member.isEmpty() || !matched) {
            throw new InvalidCredentialsException();
        }

        var authenticated = member.orElseThrow();
        var permissions = authenticated.permissions().stream()
                .map(permission -> new PermissionCode(permission.value()))
                .collect(Collectors.toUnmodifiableSet());
        return sessions.issue(new TrustedRequestContext(
                authenticated.tenantId(),
                new SubjectId(authenticated.subjectId().value()),
                new MembershipId(authenticated.membershipId().value()),
                CredentialType.PERSON,
                command.channel(),
                authenticated.permissionVersion(),
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
