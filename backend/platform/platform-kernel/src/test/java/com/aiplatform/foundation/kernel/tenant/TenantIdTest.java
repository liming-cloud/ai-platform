package com.aiplatform.foundation.kernel.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantIdTest {

    @Test
    void 应从标准文本恢复租户标识() {
        var source = UUID.fromString("10000000-0000-0000-0000-000000000001");

        var tenantId = TenantId.parse(source.toString());

        assertEquals(source, tenantId.value());
    }
}
