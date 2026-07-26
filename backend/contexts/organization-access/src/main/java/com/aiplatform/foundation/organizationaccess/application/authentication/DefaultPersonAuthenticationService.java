package com.aiplatform.foundation.organizationaccess.application.authentication;

import com.aiplatform.foundation.organizationaccess.api.PersonAuthenticationUseCase;
import com.aiplatform.foundation.organizationaccess.api.model.CredentialType;
import com.aiplatform.foundation.organizationaccess.api.model.IssuedSession;
import com.aiplatform.foundation.organizationaccess.api.model.PersonLoginCommand;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import com.aiplatform.foundation.organizationaccess.application.port.out.AuthenticatedSessionGateway;
import com.aiplatform.foundation.organizationaccess.application.port.out.PasswordVerifier;
import com.aiplatform.foundation.organizationaccess.application.port.out.TenantMemberCredentialRepository;
import java.util.Arrays;
import java.util.Objects;

/**
 * 使用持久化成员事实完成认证并签发可信会话。
 */
public final class DefaultPersonAuthenticationService implements PersonAuthenticationUseCase {

    private final TenantMemberCredentialRepository members;
    private final PasswordVerifier passwords;
    private final AuthenticatedSessionGateway sessions;
    private final String dummyEncodedPassword;

    public DefaultPersonAuthenticationService(
            TenantMemberCredentialRepository members,
            PasswordVerifier passwords,
            AuthenticatedSessionGateway sessions,
            String dummyEncodedPassword) {
        this.members = Objects.requireNonNull(members, "成员仓储不能为空");
        this.passwords = Objects.requireNonNull(passwords, "密码验证器不能为空");
        this.sessions = Objects.requireNonNull(sessions, "会话网关不能为空");
        this.dummyEncodedPassword = requireDummyPassword(dummyEncodedPassword);
    }

    @Override
    public IssuedSession authenticate(PersonLoginCommand command) {
        Objects.requireNonNull(command, "登录命令不能为空");
        var member = members.findEnabled(command.tenantCode(), command.loginName());
        var encodedPassword = member
                .map(AuthenticatedMember::encodedPassword)
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
        var context = new TrustedRequestContext(
                authenticated.tenantId(),
                authenticated.subjectId(),
                authenticated.membershipId(),
                CredentialType.PERSON,
                command.channel(),
                authenticated.permissionVersion(),
                authenticated.permissions(),
                command.traceId());
        return sessions.issue(context);
    }

    private static String requireDummyPassword(String value) {
        Objects.requireNonNull(value, "等价验证摘要不能为空");
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("等价验证摘要格式不正确");
        }
        return value;
    }
}
