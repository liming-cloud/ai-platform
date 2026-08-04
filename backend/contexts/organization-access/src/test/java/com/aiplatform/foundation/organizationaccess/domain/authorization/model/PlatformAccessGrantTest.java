package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAccessGrantTest {

    @Test
    void 缺少平台会话准入权限不能构成平台授权() {
        assertThrows(IllegalArgumentException.class, () -> new PlatformAccessGrant(
                new SubjectId(4921825928971026434L),
                1,
                Set.of(new PermissionCode("tenant-governance:write"))));
    }
}
