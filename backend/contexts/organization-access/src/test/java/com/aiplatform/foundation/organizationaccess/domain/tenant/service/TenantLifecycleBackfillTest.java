package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantLifecycleBackfillTest {

    @Test
    void 缺少经确认的初始管理员映射不得回填生命周期() {
        assertThrows(IllegalArgumentException.class, () -> TenantLifecycleBackfill.backfill(
                new TenantId(100000000000000001L),
                "demo-store",
                "演示门店",
                null,
                Instant.parse("2026-08-03T01:00:00Z")));
    }
}
