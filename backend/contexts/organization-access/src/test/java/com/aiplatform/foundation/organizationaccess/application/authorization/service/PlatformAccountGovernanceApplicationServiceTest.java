package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformAccountStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlatformAccountGovernanceApplicationServiceTest {

    @Test
    void 禁用后必须撤销目标主体的全部会话族且撤销失败不得返回成功() {
        var store = new RecordingStore();
        var revoked = new AtomicLong();
        var service = new PlatformAccountGovernanceApplicationService(store, subjectId -> {
            revoked.set(subjectId.value());
            throw new IllegalStateException("会话族撤销不可用");
        });

        assertThrows(IllegalStateException.class, () -> service.disable(accountAdministrator(), 102L));

        assertEquals(102L, revoked.get());
        assertEquals("DISABLED", store.updated.status().name());
        assertEquals(1L, store.updated.version());
    }

    @Test
    void 无账户治理权限的平台注册上下文不得禁用账户() {
        PlatformAccountStore store = new RecordingStore();
        var service = new PlatformAccountGovernanceApplicationService(store, subjectId -> { });
        var context = new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(101L),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE,
                0L,
                java.util.Set.of(PlatformPermissions.SESSION_ACCESS),
                "trc_account_governance");

        assertThrows(IllegalArgumentException.class, () -> service.disable(context, 102L));
    }

    @Test
    void 启用后必须撤销目标主体的全部会话族() {
        var store = new RecordingStore(
                com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus.DISABLED);
        var revoked = new AtomicLong();
        var service = new PlatformAccountGovernanceApplicationService(store, subjectId -> revoked.set(subjectId.value()));

        service.enable(accountAdministrator(), 102L);

        assertEquals(102L, revoked.get());
        assertEquals("ACTIVE", store.updated.status().name());
        assertEquals(1L, store.updated.version());
    }

    private static PlatformRequestContext accountAdministrator() {
        return new PlatformRequestContext(
                new com.aiplatform.foundation.security.identity.SubjectId(101L),
                com.aiplatform.foundation.security.identity.CredentialType.PERSON,
                com.aiplatform.foundation.security.identity.AccessChannel.CONSOLE,
                0L,
                java.util.Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.ACCOUNT_MANAGE),
                "trc_account_governance");
    }

    private static final class RecordingStore implements PlatformAccountStore {

        private SubjectIdentity updated;
        private final com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus targetStatus;

        private RecordingStore() {
            this(com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus.ACTIVE);
        }

        private RecordingStore(
                com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        @Override
        public Optional<SubjectIdentity> lock(SubjectId subjectId) {
            return Optional.of(SubjectIdentity.reconstitute(
                    subjectId,
                    "target-account",
                    new EncodedPassword("test-password-hash"),
                    targetStatus,
                    0L,
                    Instant.parse("2026-08-11T00:00:00Z")));
        }

        @Override
        public boolean hasActiveAccountAdministratorRole(SubjectId subjectId) {
            return false;
        }

        @Override
        public long countOtherActiveAccountAdministrators(SubjectId subjectId) {
            return 1L;
        }

        @Override
        public boolean insert(SubjectIdentity identity) {
            throw new AssertionError("账户治理状态变更不得建立账户");
        }

        @Override
        public void update(SubjectIdentity identity) {
            updated = identity;
        }

        @Override
        public com.aiplatform.foundation.shared.contract.paging.PageResult<com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountSummary> list(
                com.aiplatform.foundation.shared.contract.paging.PageQuery query) {
            throw new AssertionError("禁用用例不得读取账户目录");
        }
    }
}
