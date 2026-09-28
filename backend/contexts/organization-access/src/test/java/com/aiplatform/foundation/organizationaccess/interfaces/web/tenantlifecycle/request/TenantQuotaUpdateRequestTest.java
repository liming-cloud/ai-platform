package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantQuotaUpdateRequestTest {

    @Test
    void 仅允许五项配置与预期版本且保留字节精度() {
        var request = TenantQuotaUpdateRequest.from(valid());
        assertEquals(Long.MAX_VALUE, request.bytesPerKnowledgeBase());
        assertEquals(7L, request.expectedVersion());
    }

    @Test
    void 拒绝额外字段缺少字段和不精确容量() {
        var extra = valid(); extra.put("tenantId", 99L);
        assertThrows(IllegalArgumentException.class, () -> TenantQuotaUpdateRequest.from(extra));
        var missing = valid(); missing.remove("knowledgeBases");
        assertThrows(IllegalArgumentException.class, () -> TenantQuotaUpdateRequest.from(missing));
        var numberCapacity = valid(); numberCapacity.put("bytesPerKnowledgeBase", 10_000_000_000L);
        assertThrows(IllegalArgumentException.class, () -> TenantQuotaUpdateRequest.from(numberCapacity));
        var overflow = valid(); overflow.put("bytesPerKnowledgeBase", "9223372036854775808");
        assertThrows(IllegalArgumentException.class, () -> TenantQuotaUpdateRequest.from(overflow));
    }

    @Test
    void 拒绝浮点数量和负版本() {
        var fractional = valid(); fractional.put("knowledgeBases", 2.5);
        assertThrows(IllegalArgumentException.class, () -> TenantQuotaUpdateRequest.from(fractional));
        var version = valid(); version.put("expectedVersion", -1);
        assertThrows(IllegalArgumentException.class, () -> TenantQuotaUpdateRequest.from(version));
    }

    private static Map<String, Object> valid() {
        return new HashMap<>(Map.of(
                "knowledgeBases", 3,
                "documentsPerKnowledgeBase", 1000,
                "bytesPerKnowledgeBase", Long.toString(Long.MAX_VALUE),
                "maxFileBytes", "50000000",
                "maxTextBytes", "1048576",
                "expectedVersion", 7));
    }
}
