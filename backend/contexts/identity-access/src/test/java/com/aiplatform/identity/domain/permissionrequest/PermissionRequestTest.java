package com.aiplatform.identity.domain.permissionrequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PermissionRequestTest {
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    void rejectsRequesterAsApproverOrRejectorAtTheAggregateBoundary() {
        PermissionRequest request = new PermissionRequest(
                TenantId.of("2001"),
                "request-1",
                "1001",
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use",
                "完成合同审核",
                PermissionRequestStatus.PENDING,
                null,
                null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)
        );

        for (String action : new String[] {"APPROVE", "REJECT"}) {
            assertThatThrownBy(() -> request.decide(action, request.requesterId(), "自行处理", NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("申请人不能处理自己的权限申请");
        }
    }
}
