package com.aiplatform.foundation.organizationaccess.domain.tenant.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import org.junit.jupiter.api.Test;

class TenantQuotaTest {

    private static final TenantId TENANT_ID = new TenantId(201L);

    @Test
    void 应保留租户归属及最大整数容量不丢失精度() {
        var quota = new TenantQuota(TENANT_ID, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0);

        assertEquals(TENANT_ID, quota.tenantId());
        assertEquals(Integer.MAX_VALUE, quota.knowledgeBases());
        assertEquals(Integer.MAX_VALUE, quota.documentsPerKnowledgeBase());
        assertEquals(Long.MAX_VALUE, quota.bytesPerKnowledgeBase());
        assertEquals(Long.MAX_VALUE, quota.maxFileBytes());
        assertEquals(Long.MAX_VALUE, quota.maxTextBytes());
        assertEquals(0L, quota.version());
    }

    @Test
    void 应拒绝非正数量或容量() {
        for (var invalid : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantQuota(TENANT_ID, invalid, 1, 100, 10, 10, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantQuota(TENANT_ID, 1, invalid, 100, 10, 10, 0));
        }
        for (var invalid : new long[] {0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantQuota(TENANT_ID, 1, 1, invalid, 10, 10, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantQuota(TENANT_ID, 1, 1, 100, invalid, 10, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new TenantQuota(TENANT_ID, 1, 1, 100, 10, invalid, 0));
        }
    }

    @Test
    void 应拒绝缺失租户负版本和超过库容量的单文件() {
        assertThrows(NullPointerException.class,
                () -> new TenantQuota(null, 1, 1, 100, 10, 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TenantQuota(TENANT_ID, 1, 1, 100, 10, 10, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new TenantQuota(TENANT_ID, 1, 1, 100, 101, 10, 0));
    }
}
