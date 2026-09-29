package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.exception.PlatformAccountLoginNameConflictException;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

class PlatformAccountEstablishmentApplicationServiceTest {

    @Test
    void 建立账户必须校验账户治理权限并在摘要后清除初始口令() {
        var store = new RecordingStore(true);
        var bindings = mock(PlatformRoleBindingStore.class);
        var service = new PlatformAccountEstablishmentApplicationService(
                store,
                bindings,
                password -> "encoded:" + String.valueOf(password),
                new com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator() {
                    private long next = 101L;
                    public long nextId() { return ++next; }
                },
                Clock.fixed(Instant.parse("2026-08-11T04:00:00Z"), ZoneOffset.UTC));
        var password = "initial-password".toCharArray();

        var created = service.establish(accountAdministrator(), "new-account", password);

        assertEquals(102L, created.subjectId());
        assertEquals("new-account", created.loginName());
        assertEquals("ACTIVE", created.status());
        assertEquals(0L, created.version());
        assertArrayEquals(new char[password.length], password);
        assertEquals("encoded:initial-password", store.inserted.encodedPassword().verificationValue());
        assertEquals(Set.of("PLATFORM_ADMINISTRATOR"), created.activeRoleCodes());
        verify(bindings).bind(argThat(binding -> binding.subjectId().value() == 102L
                && binding.role() == PlatformRole.PLATFORM_ADMINISTRATOR));
    }

    @Test
    void 无账户治理权限不得建立人员账户() {
        var service = new PlatformAccountEstablishmentApplicationService(
                new RecordingStore(false),
                mock(PlatformRoleBindingStore.class),
                password -> "encoded:" + String.valueOf(password),
                () -> 102L,
                Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () -> service.establish(noPermission(), "new-account", "initial-password".toCharArray()));
    }

    @Test
    void 重复登录名返回稳定冲突而不是请求格式错误() {
        var service = new PlatformAccountEstablishmentApplicationService(
                new RecordingStore(false),
                mock(PlatformRoleBindingStore.class),
                password -> "encoded:" + String.valueOf(password),
                () -> 102L,
                Clock.systemUTC());

        assertThrows(PlatformAccountLoginNameConflictException.class,
                () -> service.establish(accountAdministrator(), "new-account", "initial-password".toCharArray()));
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
        public void lockAdministratorDisableGate() {
            throw new AssertionError("建立账户不得获取停用门禁");
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
        public void recordOperation(long operatorSubjectId, long targetSubjectId, String operation, long targetVersion) {
            if (targetSubjectId != 102L || !operation.equals("CREATE")) throw new AssertionError("创建审计目标或操作错误");
        }

        @Override
        public com.aiplatform.foundation.shared.contract.paging.PageResult<com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary> list(
                com.aiplatform.foundation.shared.contract.paging.PageQuery query, String loginName, String status) {
            throw new AssertionError("建立账户不得读取目录");
        }
    }
}
