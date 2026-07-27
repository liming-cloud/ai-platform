package com.aiplatform.foundation.kernel.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void 应从十进制文本恢复正数租户标识() {
        var source = 100000000000000001L;

        var tenantId = TenantId.parse(Long.toString(source));

        assertEquals(source, tenantId.value());
    }

    @Test
    void 应拒绝非正数和旧格式租户标识() {
        assertThrows(IllegalArgumentException.class, () -> new TenantId(0));
        assertThrows(IllegalArgumentException.class, () -> TenantId.parse("-1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> TenantId.parse("10000000-0000-0000-0000-000000000001"));
    }
}
