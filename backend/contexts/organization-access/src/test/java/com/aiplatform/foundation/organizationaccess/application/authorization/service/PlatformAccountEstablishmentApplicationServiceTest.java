package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAccountEstablishmentApplicationServiceTest {

    @Test
    void 建立账户必须校验账户治理权限并在摘要后清除初始口令() {
        var store = new RecordingStore(true);
        var service = new PlatformAccountEstablishmentApplicationService(
                store,
                password -> "encoded:" + String.valueOf(password),
                () -> 102L,
                Clock.fixed(Instant.parse("2026-08-11T04:00:00Z"), ZoneOffset.UTC));
        var password = "initial-password".toCharArray();

        var created = service.establish(accountAdministrator(), "new-account", password);

        assertEquals(102L, created.subjectId());
        assertEquals("new-account", created.loginName());
        assertEquals("ACTIVE", created.status());
        assertEquals(0L, created.version());
        assertArrayEquals(new char[password.length], password);
        assertEquals("encoded:initial-password", store.inserted.encodedPassword().verificationValue());
    }

    @Test
    void 无账户治理权限或重复登录名不得建立平台账户() {
        var service = new PlatformAccountEstablishmentApplicationService(
                new RecordingStore(false),
                password -> "encoded:" + String.valueOf(password),
                () -> 102L,
                Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () -> service.establish(noPermission(), "new-account", "initial-password".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> service.establish(accountAdministrator(), "new-account", "initial-password".toCharArray()));
    }

    private static PlatformRequestContext accountAdministrator() {
        return context(Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE));
    }

    private static PlatformRequestContext noPermission() {
        return context(Set.of(PlatformPermissions.SESSION_ACCESS));
    }

    private static PlatformRequestContext context(Set<PermissionCode> permissions) {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(101L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                0L,
                permissions,
                "trc_account_establishment");
    }

    private static final class RecordingStore implements PlatformAccountStore {

        private final boolean insertResult;
        private SubjectIdentity inserted;

        private RecordingStore(boolean insertResult) {
            this.insertResult = insertResult;
        }

        @Override
        public Optional<SubjectIdentity> lock(SubjectId subjectId) {
            throw new AssertionError("建立账户不得锁定既有账户");
        }

        @Override
        public boolean hasActiveAccountAdministratorRole(SubjectId subjectId) {
            throw new AssertionError("建立账户不得读取角色绑定");
        }

        @Override
        public long countOtherActiveAccountAdministrators(SubjectId subjectId) {
            throw new AssertionError("建立账户不得读取角色绑定");
        }

        @Override
        public boolean insert(SubjectIdentity identity) {
            inserted = identity;
            return insertResult;
        }

        @Override
        public void update(SubjectIdentity identity) {
            throw new AssertionError("建立账户不得更新既有账户");
        }

        @Override
        public com.aiplatform.foundation.shared.contract.paging.PageResult<com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary> list(
                com.aiplatform.foundation.shared.contract.paging.PageQuery query) {
            throw new AssertionError("建立账户不得读取目录");
        }
    }
}
