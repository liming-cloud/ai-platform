package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantLoginSecurityUpdateRequestTest {
    @Test
    void 只接受完整三项整数和预期版本() {
        var input = new HashMap<String, Object>(Map.of(
                "sessionHours", 4, "failedAttempts", 3, "lockMinutes", 30, "expectedVersion", 2L));
        var parsed = TenantLoginSecurityUpdateRequest.from(input);
        assertEquals(4, parsed.sessionHours());
        assertEquals(2, parsed.expectedVersion());

        input.put("isolation", false);
        assertThrows(IllegalArgumentException.class, () -> TenantLoginSecurityUpdateRequest.from(input));
        input.remove("isolation");
        input.put("sessionHours", 4.0);
        assertThrows(IllegalArgumentException.class, () -> TenantLoginSecurityUpdateRequest.from(input));
        input.put("sessionHours", 4);
        input.remove("failedAttempts");
        assertThrows(IllegalArgumentException.class, () -> TenantLoginSecurityUpdateRequest.from(input));
    }
}
