package com.aiplatform.foundation.organizationaccess.domain.authorization.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformAccountGovernanceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-11T00:00:00Z");
    private static final SubjectId OPERATOR = new SubjectId(101L);
    private static final SubjectId TARGET = new SubjectId(102L);

    @Test
    void 禁用最后一个账户管理员或自身必须被拒绝() {
        var target = identity(TARGET);

        assertThrows(IllegalStateException.class, () -> PlatformAccountGovernance.disable(
                OPERATOR, target, true, 0L));
        assertThrows(IllegalArgumentException.class, () -> PlatformAccountGovernance.disable(
                TARGET, target, false, 1L));
    }

    @Test
    void 禁用与受控重置应返回修订后的账户和会话撤销目标() {
        var target = identity(TARGET);

        var disabled = PlatformAccountGovernance.disable(OPERATOR, target, true, 1L);
        var reset = PlatformAccountGovernance.resetPassword(
                OPERATOR, disabled.identity(), new EncodedPassword("new-encoded"));

        assertFalse(disabled.identity().isActive());
        assertEquals(1L, disabled.identity().version());
        assertEquals(TARGET, disabled.sessionRevocationSubjectId());
        assertEquals(2L, reset.identity().version());
        assertEquals(TARGET, reset.sessionRevocationSubjectId());
    }

    @Test
    void 不得由自身重置凭据() {
        assertThrows(IllegalArgumentException.class, () -> PlatformAccountGovernance.resetPassword(
                TARGET, identity(TARGET), new EncodedPassword("new-encoded")));
    }

    @Test
    void 启用禁用账户应推进修订并要求撤销旧会话族() {
        var disabled = identity(TARGET).disable();

        var enabled = PlatformAccountGovernance.enable(OPERATOR, disabled);

        assertEquals(true, enabled.identity().isActive());
        assertEquals(2L, enabled.identity().version());
        assertEquals(TARGET, enabled.sessionRevocationSubjectId());
    }

    private static SubjectIdentity identity(SubjectId id) {
        return SubjectIdentity.register(id, "account-" + id.value(), new EncodedPassword("encoded"), CREATED_AT);
    }
}
