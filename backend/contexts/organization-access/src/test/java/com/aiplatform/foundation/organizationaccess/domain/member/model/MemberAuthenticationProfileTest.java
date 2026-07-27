package com.aiplatform.foundation.organizationaccess.domain.member.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionGrantSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemberAuthenticationProfileTest {

    @Test
    void 应保存同一成员的身份凭据和授权事实() {
        var permission = new PermissionCode("knowledge:read");
        var profile = new MemberAuthenticationProfile(
                new TenantId(101L),
                new SubjectId(102L),
                new MemberId(103L),
                new EncodedPassword("encoded"),
                new PermissionGrantSet(7, Set.of(permission)));

        assertEquals(new TenantId(101L), profile.tenantId());
        assertEquals(new SubjectId(102L), profile.subjectId());
        assertEquals(new MemberId(103L), profile.id());
        assertEquals(7, profile.permissionVersion());
        assertEquals(Set.of(permission), profile.permissions());
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
