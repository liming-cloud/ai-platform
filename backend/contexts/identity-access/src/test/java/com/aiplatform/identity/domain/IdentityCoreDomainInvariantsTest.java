package com.aiplatform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityCoreDomainInvariantsTest {
    private static final Instant CREATED_AT = Instant.parse("2026-05-21T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-05-21T01:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-05-21T02:00:00Z");
    private static final PasswordHash PASSWORD_HASH = PasswordHash.of("pbkdf2-sha256$1000$c2FsdA==$aGFzaA==");

    @Test
    void enterpriseUserRejectsBlankCoreFactsInsteadOfNullOnlyValidation() {
        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                " ",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                " ",
                "平台管理员",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                " ",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of(" "),
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of(),
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码集合不能为空");
    }

    @Test
    void enterpriseUserRejectsMissingCoreFactsInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> new EnterpriseUser(
                null,
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户 ID不能为空");

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                null,
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("角色编码集合不能为空");

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                null,
                CREATED_AT,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户状态不能为空");

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                null,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("创建时间不能为空");

        assertThatThrownBy(() -> new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                CREATED_AT,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("更新时间不能为空");
    }

    @Test
    void enterpriseUserNormalizesCoreFactsBeforeUse() {
        EnterpriseUser user = new EnterpriseUser(
                TenantId.of("2001"),
                " 1001 ",
                " admin ",
                " 平台管理员 ",
                Set.of(" platform_admin ", "auditor"),
                UserStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT
        );

        assertThat(user.userId()).isEqualTo("1001");
        assertThat(user.loginName()).isEqualTo("admin");
        assertThat(user.displayName()).isEqualTo("平台管理员");
        assertThat(user.roleCodes()).containsExactlyInAnyOrder("platform_admin", "auditor");
    }

    @Test
    void loginSessionAndAccessTokenRejectBlankFactsInsteadOfNullOnlyValidation() {
        assertThatThrownBy(() -> new AccessToken(" ", EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginSession(
                TenantId.of("2001"),
                " ",
                "1001",
                new AccessToken("token-1001", EXPIRES_AT),
                CREATED_AT,
                true
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginSession(
                TenantId.of("2001"),
                "session-1001",
                " ",
                new AccessToken("token-1001", EXPIRES_AT),
                CREATED_AT,
                true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginSessionAndAccessTokenRejectMissingFactsInsteadOfJdkNullPointerException() {
        AccessToken token = new AccessToken("token-1001", EXPIRES_AT);

        assertThatThrownBy(() -> new AccessToken("token-1001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("令牌过期时间不能为空");
        assertThatThrownBy(() -> token.isExpired(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("校验时间不能为空");
        assertThatThrownBy(() -> new LoginSession(
                null,
                "session-1001",
                "1001",
                token,
                CREATED_AT,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户 ID不能为空");
        assertThatThrownBy(() -> new LoginSession(
                TenantId.of("2001"),
                "session-1001",
                "1001",
                null,
                CREATED_AT,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("访问令牌不能为空");
        assertThatThrownBy(() -> new LoginSession(
                TenantId.of("2001"),
                "session-1001",
                "1001",
                token,
                null,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("创建时间不能为空");
        assertThatThrownBy(() -> new LoginSession(
                TenantId.of("2001"),
                "session-1001",
                "1001",
                token,
                CREATED_AT,
                true
        ).canBeUsedAt(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("校验时间不能为空");
    }

    @Test
    void loginSessionAndAccessTokenNormalizeFactsBeforeUse() {
        AccessToken token = new AccessToken(" token-1001 ", EXPIRES_AT);
        LoginSession session = new LoginSession(
                TenantId.of("2001"),
                " session-1001 ",
                " 1001 ",
                token,
                CREATED_AT,
                true
        );

        assertThat(token.tokenValue()).isEqualTo("token-1001");
        assertThat(session.sessionId()).isEqualTo("session-1001");
        assertThat(session.userId()).isEqualTo("1001");
    }

    @Test
    void passwordCredentialRejectsBlankUserIdInsteadOfNullOnlyValidation() {
        assertThatThrownBy(() -> new PasswordCredential(
                TenantId.of("2001"),
                " ",
                PASSWORD_HASH,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passwordCredentialRejectsMissingFactsInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> new PasswordCredential(
                null,
                "1001",
                PASSWORD_HASH,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户 ID不能为空");
        assertThatThrownBy(() -> new PasswordCredential(
                TenantId.of("2001"),
                "1001",
                null,
                UPDATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码哈希不能为空");
        assertThatThrownBy(() -> new PasswordCredential(
                TenantId.of("2001"),
                "1001",
                PASSWORD_HASH,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("更新时间不能为空");
        assertThatThrownBy(() -> PasswordHash.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码哈希不能为空");
    }

    @Test
    void passwordCredentialNormalizesUserIdBeforeUse() {
        PasswordCredential credential = new PasswordCredential(
                TenantId.of("2001"),
                " 1001 ",
                PASSWORD_HASH,
                UPDATED_AT
        );

        assertThat(credential.userId()).isEqualTo("1001");
    }
}
