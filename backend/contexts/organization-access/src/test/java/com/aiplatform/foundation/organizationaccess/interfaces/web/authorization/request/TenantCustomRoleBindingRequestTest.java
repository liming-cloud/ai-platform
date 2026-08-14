package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantCustomRoleBindingRequestTest {

    @Test
    void 接受浏览器提交的十进制字符串角色标识() {
        var request = TenantCustomRoleBindingRequest.from(Map.of(
                "customRoleId", "2088068564064927745"));

        assertEquals(2088068564064927745L, request.customRoleId());
    }
}
