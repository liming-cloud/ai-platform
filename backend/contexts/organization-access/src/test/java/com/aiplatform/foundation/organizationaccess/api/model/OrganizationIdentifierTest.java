package com.aiplatform.foundation.organizationaccess.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OrganizationIdentifierTest {

    @Test
    void 应只接受正数主体和成员标识() {
        assertEquals(200000000000000001L, new SubjectId(200000000000000001L).value());
        assertEquals(300000000000000001L, new MembershipId(300000000000000001L).value());
        assertThrows(IllegalArgumentException.class, () -> new SubjectId(0));
        assertThrows(IllegalArgumentException.class, () -> new MembershipId(-1));
    }

    @Test
    void 应从十进制文本解析标识并拒绝旧格式() {
        assertEquals(
                new SubjectId(200000000000000001L),
                SubjectId.parse("200000000000000001"));
        assertEquals(
                new MembershipId(300000000000000001L),
                MembershipId.parse("300000000000000001"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MembershipId.parse("30000000-0000-0000-0000-000000000001"));
    }
}
