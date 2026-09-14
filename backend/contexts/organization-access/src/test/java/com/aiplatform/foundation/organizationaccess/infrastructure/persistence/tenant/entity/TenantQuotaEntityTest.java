package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantQuota;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import org.junit.jupiter.api.Test;

class TenantQuotaEntityTest {

    @Test
    void 应完整往返租户标识版本和最大容量() {
        var quota = new TenantQuota(new TenantId(201L), 7, 1200,
                Long.MAX_VALUE, 50_000_000L, 1_048_576L, 4);

        assertEquals(quota, TenantQuotaEntity.from(quota).toDomain());
    }

    @Test
    void 读取损坏的配额必须重新通过领域校验() {
        var row = TenantQuotaEntity.from(new TenantQuota(
                new TenantId(201L), 3, 1000, 10_000_000_000L, 50_000_000L, 1_048_576L, 0));
        row.setMaxFileBytes(10_000_000_001L);

        assertThrows(IllegalArgumentException.class, row::toDomain);
    }
}
