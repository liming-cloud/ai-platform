package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformAccountPasswordResetRequestTest {

    @Test
    void 严格请求拒绝主体和权限字段并在结束后清除口令副本() {
        assertThrows(IllegalArgumentException.class, () -> PlatformAccountPasswordResetRequest.from(Map.of(
                "password", "new-password",
                "subjectId", "102")));

        var request = PlatformAccountPasswordResetRequest.from(Map.of("password", "new-password"));
        request.clear();

        assertArrayEquals(new char["new-password".length()], request.passwordCopy());
    }
}
