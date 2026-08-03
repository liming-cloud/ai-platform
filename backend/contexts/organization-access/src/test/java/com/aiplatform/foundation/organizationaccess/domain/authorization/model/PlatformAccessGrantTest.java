package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAccessGrantTest {

    @Test
    void 仅模型平台权限不能构成租户治理授权() {
        assertThrows(IllegalArgumentException.class, () -> new PlatformAccessGrant(
                new SubjectId(4921825928971026434L),
                1,
                Set.of(new PermissionCode("model-platform:write"))));
    }
}
