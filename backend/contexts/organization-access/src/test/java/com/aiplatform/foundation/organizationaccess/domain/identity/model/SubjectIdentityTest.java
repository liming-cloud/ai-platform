package com.aiplatform.foundation.organizationaccess.domain.identity.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SubjectIdentityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    void 应以全局主体标识保存一次凭据并生成不可变禁用修订() {
        var identity = SubjectIdentity.register(
                new SubjectId(101L), "platform-admin", new EncodedPassword("encoded"), CREATED_AT);

        assertEquals(new SubjectId(101L), identity.id());
        assertEquals("platform-admin", identity.loginName());
        assertTrue(identity.isActive());
        assertEquals(0L, identity.version());
        assertEquals(CREATED_AT, identity.createdAt());

        var disabled = identity.disable();

        assertFalse(disabled.isActive());
        assertEquals(1L, disabled.version());
        assertEquals("[已脱敏]", disabled.encodedPassword().toString());
        assertSame(disabled, disabled.disable());
    }

    @Test
    void 应拒绝不合法的全局登录名或空白凭据() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SubjectIdentity.register(
                        new SubjectId(101L), "平台管理员", new EncodedPassword("encoded"), CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> SubjectIdentity.register(
                        new SubjectId(101L), "platform-admin", new EncodedPassword(" "), CREATED_AT));
    }

    @Test
    void 启用和受控凭据重置都应生成新的不可变修订() {
        var identity = SubjectIdentity.register(
                new SubjectId(101L), "platform-admin", new EncodedPassword("encoded"), CREATED_AT)
                .disable();

        var enabled = identity.enable();
        var passwordReset = enabled.resetPassword(new EncodedPassword("new-encoded"));

        assertTrue(enabled.isActive());
        assertEquals(2L, enabled.version());
        assertEquals(3L, passwordReset.version());
        assertEquals("[已脱敏]", passwordReset.encodedPassword().toString());
    }
}
