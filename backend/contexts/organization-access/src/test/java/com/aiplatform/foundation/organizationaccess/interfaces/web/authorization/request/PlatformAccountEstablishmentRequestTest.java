package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformAccountEstablishmentRequestTest {

    @Test
    void 严格请求拒绝主体角色和权限字段并在结束后清除口令副本() {
        assertThrows(IllegalArgumentException.class, () -> PlatformAccountEstablishmentRequest.from(Map.of(
                "loginName", "new-account",
                "password", "initial-password",
                "role", "PLATFORM_ADMINISTRATOR")));

        var request = PlatformAccountEstablishmentRequest.from(Map.of(
                "loginName", "new-account",
                "password", "initial-password"));
        request.clear();

        assertArrayEquals(new char["initial-password".length()], request.passwordCopy());
    }
}
