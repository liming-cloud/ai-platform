package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformRoleBindingGovernanceTest {

    @Test
    void 已启用账户可绑定未持有的平台固定角色() {
        var binding = PlatformRoleBindingGovernance.bind(
                account(SubjectIdentityStatus.ACTIVE),
                false,
                new PlatformRoleBindingId(1001L),
                PlatformRole.PLATFORM_ADMINISTRATOR,
                Instant.parse("2026-08-11T04:00:00Z"));

        assertEquals(PlatformRole.PLATFORM_ADMINISTRATOR, binding.role());
        assertEquals(101L, binding.subjectId().value());
        assertEquals(0L, binding.version());
    }

    @Test
    void 未启用账户不得绑定平台固定角色() {
        assertThrows(
                IllegalStateException.class,
                () -> PlatformRoleBindingGovernance.bind(
                        account(SubjectIdentityStatus.DISABLED),
                        false,
                        new PlatformRoleBindingId(1001L),
                        PlatformRole.PLATFORM_ADMINISTRATOR,
                        Instant.parse("2026-08-11T04:00:00Z")));
    }

    @Test
    void 不得撤销最后一个有效账户管理员角色() {
        var binding = PlatformRoleBinding.bind(
                new PlatformRoleBindingId(1001L),
                new SubjectId(101L),
                PlatformRole.PLATFORM_ADMINISTRATOR,
                Instant.parse("2026-08-11T04:00:00Z"));

        assertThrows(
                IllegalStateException.class,
                () -> PlatformRoleBindingGovernance.revoke(
                        account(SubjectIdentityStatus.ACTIVE),
                        binding,
                        0L,
                        Instant.parse("2026-08-11T05:00:00Z")));
    }

    private static SubjectIdentity account(SubjectIdentityStatus status) {
        return SubjectIdentity.reconstitute(
                new SubjectId(101L),
                "platform-account",
                new EncodedPassword("test-password-hash"),
                status,
                0L,
                Instant.parse("2026-08-11T00:00:00Z"));
    }
}
