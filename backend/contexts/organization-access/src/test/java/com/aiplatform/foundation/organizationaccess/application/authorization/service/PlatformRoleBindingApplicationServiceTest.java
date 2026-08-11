package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlatformRoleBindingApplicationServiceTest {

    @Test
    void 账户管理员绑定固定角色后必须撤销目标全部会话族() {
        var store = new RecordingStore();
        var revoked = new AtomicLong();
        var service = new PlatformRoleBindingApplicationService(
                store,
                subjectId -> revoked.set(subjectId.value()),
                () -> 3001L,
                Clock.fixed(Instant.parse("2026-08-11T06:00:00Z"), ZoneOffset.UTC));

        var binding = service.bind(accountAdministrator(), 102L, "PLATFORM_OPERATOR");

        assertEquals("PLATFORM_OPERATOR", binding.roleCode());
        assertEquals(3001L, binding.bindingId());
        assertEquals(102L, revoked.get());
        assertEquals(1L, store.authorizationRevisionCount);
        assertEquals(PlatformRole.PLATFORM_OPERATOR, store.saved.role());
    }

    @Test
    void 非账户管理员不得绑定平台角色() {
        var service = new PlatformRoleBindingApplicationService(
                new RecordingStore(),
                subjectId -> { },
                () -> 3001L,
                Clock.systemUTC());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.bind(platformOperator(), 102L, "PLATFORM_OPERATOR"));
    }

    private static PlatformRequestContext accountAdministrator() {
        return context(Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE));
    }

    private static PlatformRequestContext platformOperator() {
        return context(Set.of(PlatformPermissions.SESSION_ACCESS));
    }

    private static PlatformRequestContext context(Set<com.aiplatform.foundation.security.identity.PermissionCode> permissions) {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(101L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                0L,
                permissions,
                "trc_platform_role_binding");
    }

    private static final class RecordingStore implements PlatformRoleBindingStore {

        private PlatformRoleBinding saved;
        private long authorizationRevisionCount;

        @Override
        public Optional<SubjectIdentity> lock(SubjectId subjectId) {
            return Optional.of(SubjectIdentity.reconstitute(
                    subjectId,
                    "target-account",
                    new EncodedPassword("test-password-hash"),
                    SubjectIdentityStatus.ACTIVE,
                    0L,
                    Instant.parse("2026-08-11T00:00:00Z")));
        }

        @Override
        public boolean hasActiveBinding(SubjectId subjectId, PlatformRole role) {
            return false;
        }

        @Override
        public Optional<PlatformRoleBinding> lockActiveBinding(SubjectId subjectId, PlatformRole role) {
            return Optional.empty();
        }

        @Override
        public long countOtherActiveAccountAdministrators(SubjectId subjectId) {
            return 1L;
        }

        @Override
        public void bind(PlatformRoleBinding binding) {
            saved = binding;
            authorizationRevisionCount++;
        }

        @Override
        public void revoke(PlatformRoleBinding binding) {
            saved = binding;
            authorizationRevisionCount++;
        }
    }
}
