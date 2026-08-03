package com.aiplatform.foundation.organizationaccess.domain.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleAction;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 验证生命周期迁移领域服务只接受聚合根定义的合法状态机。
 */
class TenantLifecycleTransitionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-03T03:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T04:00:00Z");

    @Test
    void 激活必须返回迁移后根与同版本的最小状态事实() {
        var result = TenantLifecycleTransition.transition(
                provisioning(), TenantLifecycleAction.ACTIVATE, OCCURRED_AT);

        assertEquals("ACTIVE", result.lifecycle().status().name());
        assertEquals(1L, result.lifecycle().version());
        assertEquals(result.lifecycle().status(), result.change().status());
        assertEquals(result.lifecycle().version(), result.change().version());
        assertEquals(OCCURRED_AT, result.change().occurredAt());
    }

    @Test
    void 准备中租户不得跳过激活直接挂起() {
        assertThrows(IllegalStateException.class, () -> TenantLifecycleTransition.transition(
                provisioning(), TenantLifecycleAction.SUSPEND, OCCURRED_AT));
    }

    private static TenantLifecycle provisioning() {
        return TenantLifecycle.provision(
                new TenantId(100000000000000001L),
                "fresh-tenant",
                "新租户",
                new SubjectId(4921825928971026434L),
                CREATED_AT);
    }
}
