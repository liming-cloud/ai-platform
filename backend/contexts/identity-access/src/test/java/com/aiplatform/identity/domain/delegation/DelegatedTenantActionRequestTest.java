package com.aiplatform.identity.domain.delegation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DelegatedTenantActionRequestTest {
    @Test
    void normalizesRolePermissionReplacementAndBuildsStableTarget() {
        DelegatedTenantActionRequest request = new DelegatedTenantActionRequest(
                DelegatedTenantActionType.REPLACE_ROLE_PERMISSIONS,
                " 3002 ",
                null,
                List.of("permission:b", "permission:a", "permission:a"),
                " 应急修复租户授权 ",
                " request-001 "
        );

        assertThat(request.targetResourceType()).isEqualTo("IAM_ROLE");
        assertThat(request.targetResourceId()).isEqualTo("3002");
        assertThat(request.permissionCodes()).containsExactly("permission:a", "permission:b");
        assertThat(request.reason()).isEqualTo("应急修复租户授权");
    }

    @Test
    void rejectsDelegationWithoutReason() {
        assertThatThrownBy(() -> new DelegatedTenantActionRequest(
                DelegatedTenantActionType.REPLACE_ROLE_PERMISSIONS,
                "3002",
                null,
                List.of(),
                " ",
                "request-002"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("代管原因不能为空");
    }

    @Test
    void rejectsTenantAdminAssignmentWithoutUser() {
        assertThatThrownBy(() -> new DelegatedTenantActionRequest(
                DelegatedTenantActionType.ASSIGN_TENANT_ADMIN,
                "3002",
                null,
                List.of(),
                "租户初始化",
                "request-003"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目标用户 ID不能为空");
    }

    @Test
    void rejectsUnknownActionType() {
        assertThatThrownBy(() -> DelegatedTenantActionType.from("RUN_SQL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的代管动作类型");
    }

    @Test
    void canonicalPayloadCannotCollideThroughDelimiterPlacement() {
        DelegatedTenantActionRequest first = new DelegatedTenantActionRequest(
                DelegatedTenantActionType.REPLACE_ROLE_PERMISSIONS,
                "3002",
                null,
                List.of("permission:a"),
                "reason|part",
                "key"
        );
        DelegatedTenantActionRequest second = new DelegatedTenantActionRequest(
                DelegatedTenantActionType.REPLACE_ROLE_PERMISSIONS,
                "3002",
                null,
                List.of("permission:a"),
                "reason",
                "part|key"
        );

        assertThat(first.canonicalPayload()).isNotEqualTo(second.canonicalPayload());
    }
}
