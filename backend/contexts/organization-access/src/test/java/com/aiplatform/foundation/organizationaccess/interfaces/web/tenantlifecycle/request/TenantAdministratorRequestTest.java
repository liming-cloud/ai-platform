package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantAdministratorRequestTest {

    @Test
    void 分配请求只接受账户引用() {
        assertEquals(801L, TenantAdministratorAssignmentRequest.from(
                Map.of("accountReference", "801")).accountReferenceValue());
        assertThrows(IllegalArgumentException.class, () -> TenantAdministratorAssignmentRequest.from(
                Map.of("accountReference", "801", "subjectId", "101")));
    }

    @Test
    void 状态请求拒绝小数负数和多余身份字段() {
        assertEquals(4L, TenantAdministratorVersionRequest.from(
                Map.of("expectedVersion", 4)).expectedVersion());
        assertThrows(IllegalArgumentException.class, () -> TenantAdministratorVersionRequest.from(
                Map.of("expectedVersion", 4.5)));
        assertThrows(IllegalArgumentException.class, () -> TenantAdministratorVersionRequest.from(
                Map.of("expectedVersion", -1)));
        assertThrows(IllegalArgumentException.class, () -> TenantAdministratorVersionRequest.from(
                Map.of("expectedVersion", 4, "operator", "admin")));
    }

    @Test
    void 替换请求只接受旧版本和新账户引用() {
        var request = TenantAdministratorReplacementRequest.from(Map.of(
                "expectedVersion", 5,
                "replacementAccountReference", "802"));
        assertEquals(5L, request.expectedVersion());
        assertEquals(802L, request.replacementAccountReferenceValue());
        assertThrows(IllegalArgumentException.class, () -> TenantAdministratorReplacementRequest.from(Map.of(
                "expectedVersion", 5.2,
                "replacementAccountReference", "802")));
    }
}
