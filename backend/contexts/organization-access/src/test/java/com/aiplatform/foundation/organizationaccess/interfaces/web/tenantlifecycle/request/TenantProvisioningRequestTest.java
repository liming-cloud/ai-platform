package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantProvisioningRequestTest {
    @Test
    void 新合同必须精确解析最大容量且请求结束清除密码() {
        var payload = payload();
        quotas(payload).put("bytesPerKnowledgeBase", Long.toString(Long.MAX_VALUE));
        var request = TenantProvisioningRequest.from(payload);
        assertEquals(Long.MAX_VALUE, request.command().bytesPerKnowledgeBase());
        assertFalse(request.toString().contains("测试密码"));
        assertFalse(request.command().toString().contains("测试密码"));
        request.close();
        assertArrayEquals(new char[request.passwordCopy().length], request.passwordCopy());
    }

    @ParameterizedTest
    @ValueSource(strings = {"initialAdministratorSubjectId", "subjectId", "role", "tenantId", "region", "accessChannel"})
    void 旧字段和调用方身份一律拒绝(String field) {
        var payload = payload();
        payload.put(field, "禁止字段");
        assertThrows(IllegalArgumentException.class, () -> TenantProvisioningRequest.from(payload));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 嵌套额外字段也必须拒绝() {
        var administratorPayload = payload();
        ((Map<String, Object>) administratorPayload.get("administrator")).put("role", "PLATFORM_ADMINISTRATOR");
        assertThrows(IllegalArgumentException.class, () -> TenantProvisioningRequest.from(administratorPayload));
        var quotaPayload = payload();
        quotas(quotaPayload).put("usage", 0);
        assertThrows(IllegalArgumentException.class, () -> TenantProvisioningRequest.from(quotaPayload));
    }

    @Test
    void 数量必须JSON整数容量必须十进制文本且禁止溢出浮点和负数() {
        for (Object invalid : new Object[] {0, -1, 1.5, 2147483648L, "3", null}) {
            var payload = payload();
            quotas(payload).put("knowledgeBases", invalid);
            assertThrows(IllegalArgumentException.class, () -> TenantProvisioningRequest.from(payload));
        }
        for (Object invalid : new Object[] {0, 50L, "0", "-1", "1.5", "1e9", "01", "9223372036854775808", null}) {
            var payload = payload();
            quotas(payload).put("maxFileBytes", invalid);
            assertThrows(IllegalArgumentException.class, () -> TenantProvisioningRequest.from(payload));
        }
    }

    private static Map<String, Object> payload() {
        var quotas = new HashMap<String, Object>(Map.of("knowledgeBases", 3, "documentsPerKnowledgeBase", 1000,
                "bytesPerKnowledgeBase", "10000000000", "maxFileBytes", "50000000", "maxTextBytes", "1048576"));
        return new HashMap<>(Map.of("tenantCode", "fresh-tenant", "displayName", "新租户",
                "administrator", new HashMap<>(Map.of("loginName", "fresh.admin", "password", "测试密码")), "quotas", quotas));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> quotas(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("quotas");
    }
}
