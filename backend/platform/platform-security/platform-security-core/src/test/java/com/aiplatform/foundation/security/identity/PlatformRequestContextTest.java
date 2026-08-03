package com.aiplatform.foundation.security.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PlatformRequestContextTest {

    @Test
    void 应只保存令牌派生的平台身份字段并要求租户治理权限() {
        var context = new PlatformRequestContext(
                new SubjectId(4921825928971026434L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                3L,
                Set.of(PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_platform");

        assertEquals(
                Set.of("subjectId", "credentialType", "channel", "permissionVersion", "permissions", "traceId"),
                Stream.of(PlatformRequestContext.class.getRecordComponents())
                        .map(component -> component.getName())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(true, context.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE));
    }

    @Test
    void 缺少平台租户治理权限不得构造平台上下文() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlatformRequestContext(
                        new SubjectId(4921825928971026434L),
                        CredentialType.PERSON,
                        AccessChannel.CONSOLE,
                        3L,
                        Set.of(new PermissionCode("model-platform:write")),
                        "trc_platform"));
    }
}
