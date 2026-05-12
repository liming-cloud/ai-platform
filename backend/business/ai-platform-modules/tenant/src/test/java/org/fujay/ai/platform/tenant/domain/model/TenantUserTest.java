package org.fujay.ai.platform.tenant.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TenantUserTest {

    @Test
    void systemAdminOwnsAllPlatformPermissionsAndOnlyAllowsPasswordChange() {
        TenantUser admin = TenantUser.systemAccount(10L, 1L, "admin", "平台管理员", "ENABLED");

        assertTrue(admin.enabled());
        assertTrue(admin.systemAccount());
        assertTrue(admin.grantsPlatformPermission("any:permission:code"));
        assertDoesNotThrow(admin::ensurePasswordChangeAllowed);
        assertThrows(IllegalStateException.class, admin::ensureDeleteAllowed);
        assertThrows(IllegalStateException.class, admin::ensureDisableAllowed);
        assertThrows(IllegalStateException.class, admin::ensureRoleMutationAllowed);
        assertThrows(IllegalStateException.class, () -> admin.rename("新的管理员名称"));
    }

    @Test
    void normalUserDoesNotShortCircuitAuthorizationAndCanChangeProfile() {
        TenantUser user = TenantUser.standard(11L, 1L, "operator", "运营人员", "ENABLED");

        assertTrue(user.enabled());
        assertFalse(user.systemAccount());
        assertFalse(user.grantsPlatformPermission("platform:anything"));
        assertDoesNotThrow(user::ensurePasswordChangeAllowed);
        assertDoesNotThrow(user::ensureDeleteAllowed);
        assertDoesNotThrow(user::ensureDisableAllowed);
        assertDoesNotThrow(user::ensureRoleMutationAllowed);
        assertTrue(user.rename("新名称").displayName().equals("新名称"));
    }
}
