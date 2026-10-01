package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantDirectoryFilter;
import org.junit.jupiter.api.Test;

/** 只验证本片公开查询 grammar，不把查询条件扩展为授权或任意 SQL。 */
class TenantDirectoryFilterTest {

    @Test
    void 空值和空白恢复默认且通配符保留字面值() {
        assertEquals(new TenantDirectoryFilter("", ""), new TenantDirectoryFilter(null, null));
        assertEquals("%_\\", new TenantDirectoryFilter(" %_\\ ", " ACTIVE ").query());
        assertEquals("ACTIVE", new TenantDirectoryFilter("", " ACTIVE ").status());
    }

    @Test
    void 查询边界和生命周期状态失败关闭() {
        assertEquals(64, new TenantDirectoryFilter("x".repeat(64), "PROVISIONING").query().length());
        assertThrows(IllegalArgumentException.class, () -> new TenantDirectoryFilter("x".repeat(65), ""));
        assertThrows(IllegalArgumentException.class, () -> new TenantDirectoryFilter("a\0b", ""));
        assertThrows(IllegalArgumentException.class, () -> new TenantDirectoryFilter("", "DISABLED"));
        assertThrows(IllegalArgumentException.class, () -> new TenantDirectoryFilter("", "active"));
    }
}
