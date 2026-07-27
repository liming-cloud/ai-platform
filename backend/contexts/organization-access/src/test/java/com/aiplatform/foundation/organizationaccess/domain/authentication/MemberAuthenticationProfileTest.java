package com.aiplatform.foundation.organizationaccess.domain.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.PermissionGrantSet;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberAuthenticationProfileTest {

    @Test
    void 应从同一成员事实生成可信上下文() {
        var permission = new PermissionCode("knowledge:read");
        var profile = new MemberAuthenticationProfile(
                new TenantId(101L),
                new SubjectId(102L),
                new MembershipId(103L),
                new EncodedPassword("encoded"),
                new PermissionGrantSet(7, Set.of(permission)));

        var context = profile.trustedContext(AccessChannel.CONSOLE, "trc_101");

        assertEquals(new TenantId(101L), context.tenantId());
        assertEquals(new SubjectId(102L), context.subjectId());
        assertEquals(new MembershipId(103L), context.membershipId());
        assertEquals(CredentialType.PERSON, context.credentialType());
        assertEquals(7, context.permissionVersion());
        assertEquals(Set.of(permission), context.permissions());
        assertEquals("[已脱敏]", profile.encodedPassword().toString());
    }

    @Test
    void 权限授权集应拒绝非法版本和空权限() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PermissionGrantSet(-1, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PermissionGrantSet(
                        0,
                        new java.util.HashSet<>(
                                java.util.Arrays.asList((PermissionCode) null))));
    }
}
