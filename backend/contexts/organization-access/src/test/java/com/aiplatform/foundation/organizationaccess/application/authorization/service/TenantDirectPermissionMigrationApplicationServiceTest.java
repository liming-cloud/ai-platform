package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantDirectPermissionMigrationManifest;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.directpermissionmigration.port.out.TenantDirectPermissionMigrationStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantDirectPermissionMigrationResult;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TenantDirectPermissionMigrationApplicationServiceTest {

    private static final TenantId TENANT_ID = new TenantId(100000000000000001L);
    private static final MemberId MEMBERSHIP_ID = new MemberId(3382738893456539650L);

    @Test
    void 清单快照与受锁事实一致时才提交并撤销目标成员会话() {
        var store = new RecordingStore(facts(Set.of(permission("model:read")), 3L));
        var sessions = new RecordingSessions();
        var service = new TenantDirectPermissionMigrationApplicationService(store, sessions);

        service.migrate(manifest(Set.of(permission("model:read")), 3L));

        assertEquals(1, store.commits);
        assertEquals(MEMBERSHIP_ID, sessions.revokedMemberId);
    }

    @Test
    void 历史权限快照或权限修订变化时不得提交或撤销会话() {
        var changedPermissions = new RecordingStore(facts(Set.of(permission("model:write")), 3L));
        var changedVersion = new RecordingStore(facts(Set.of(permission("model:read")), 4L));
        var firstSessions = new RecordingSessions();
        var secondSessions = new RecordingSessions();

        assertThrows(IllegalArgumentException.class, () ->
                new TenantDirectPermissionMigrationApplicationService(changedPermissions, firstSessions)
                        .migrate(manifest(Set.of(permission("model:read")), 3L)));
        assertThrows(IllegalArgumentException.class, () ->
                new TenantDirectPermissionMigrationApplicationService(changedVersion, secondSessions)
                        .migrate(manifest(Set.of(permission("model:read")), 3L)));

        assertEquals(0, changedPermissions.commits);
        assertEquals(0, changedVersion.commits);
        assertEquals(null, firstSessions.revokedMemberId);
        assertEquals(null, secondSessions.revokedMemberId);
    }

    private static TenantDirectPermissionMigrationManifest manifest(
            Set<PermissionCode> expectedPermissions, long expectedPermissionVersion) {
        return new TenantDirectPermissionMigrationManifest(
                "manifest-20260811-001",
                TENANT_ID,
                MEMBERSHIP_ID,
                expectedPermissionVersion,
                expectedPermissions,
                Set.of("TENANT_MODEL_MANAGER"),
                Set.of(),
                Set.of());
    }

    private static TenantDirectPermissionMigrationStore.Facts facts(
            Set<PermissionCode> directPermissions, long permissionVersion) {
        return new TenantDirectPermissionMigrationStore.Facts(
                TenantMembership.reconstitute(
                        MEMBERSHIP_ID,
                        TENANT_ID,
                        new SubjectId(4921825928971026434L),
                        TenantMembershipRole.MEMBER,
                        TenantMembershipStatus.ENABLED,
                        permissionVersion,
                        7L,
                        Instant.parse("2026-07-01T00:00:00Z")),
                directPermissions,
                Set.of(),
                directPermissions,
                Set.of("TENANT_MODEL_MANAGER"),
                Set.of());
    }

    private static PermissionCode permission(String value) {
        return new PermissionCode(value);
    }

    private static final class RecordingStore implements TenantDirectPermissionMigrationStore {
        private final TenantDirectPermissionMigrationStore.Facts facts;
        private int commits;

        private RecordingStore(TenantDirectPermissionMigrationStore.Facts facts) {
            this.facts = facts;
        }

        @Override
        public Optional<TenantDirectPermissionMigrationStore.Facts> lock(TenantDirectPermissionMigrationManifest manifest) {
            return Optional.of(facts);
        }

        @Override
        public void commit(
                TenantDirectPermissionMigrationManifest manifest,
                TenantDirectPermissionMigrationStore.Facts facts,
                TenantDirectPermissionMigrationResult result) {
            commits++;
        }
    }

    private static final class RecordingSessions implements TenantMemberSessionRevoker {
        private MemberId revokedMemberId;

        @Override
        public void revokeTenantSessions(MemberId membershipId) {
            revokedMemberId = membershipId;
        }
    }
}
