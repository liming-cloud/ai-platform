package com.aiplatform.identity.domain.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.identity.domain.permission.spec.AuthorizationDecisionSpec;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationDecisionSpecTest {
    @Test
    void allowsWhenPermissionSnapshotAndAclMatch() {
        TenantId tenantId = new TenantId("2001");
        PermissionCode permissionCode = new PermissionCode("knowledge-base:document:upload");
        ResourceScope scope = new ResourceScope("knowledge_base", "kb-001");
        ResourceAcl acl = new ResourceAcl(
                tenantId,
                "acl-001",
                scope,
                "user",
                "1001",
                permissionCode,
                "allow",
                null,
                true
        );

        AuthorizationDecision decision = new AuthorizationDecisionSpec().decide(
                Set.of(permissionCode),
                List.of(acl),
                scope,
                permissionCode,
                Instant.parse("2026-05-25T08:00:00Z")
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.decisionCode()).isEqualTo("allowed");
    }

    @Test
    void deniesWhenAclDoesNotMatch() {
        PermissionCode permissionCode = new PermissionCode("knowledge-base:document:upload");
        ResourceScope scope = new ResourceScope("knowledge_base", "kb-001");

        AuthorizationDecision decision = new AuthorizationDecisionSpec().decide(
                Set.of(permissionCode),
                List.of(),
                scope,
                permissionCode,
                Instant.parse("2026-05-25T08:00:00Z")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("resource_acl_missing");
        assertThat(decision.reason()).isEqualTo("资源 ACL 不包含目标权限");
    }

    @Test
    void deniesWhenAclExplicitlyDeniesPermission() {
        TenantId tenantId = new TenantId("2001");
        PermissionCode permissionCode = new PermissionCode("knowledge-base:document:upload");
        ResourceScope scope = new ResourceScope("knowledge_base", "kb-001");
        ResourceAcl acl = new ResourceAcl(
                tenantId,
                "acl-002",
                scope,
                "user",
                "1001",
                permissionCode,
                "deny",
                null,
                true
        );

        AuthorizationDecision decision = new AuthorizationDecisionSpec().decide(
                Set.of(permissionCode),
                List.of(acl),
                scope,
                permissionCode,
                Instant.parse("2026-05-25T08:00:00Z")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decisionCode()).isEqualTo("resource_acl_denied");
    }

    @Test
    void rejectsUnknownAclEffectInsteadOfDenyingSilently() {
        PermissionCode permissionCode = new PermissionCode("knowledge-base:document:upload");
        ResourceScope scope = new ResourceScope("knowledge_base", "kb-001");

        assertThatThrownBy(() -> new ResourceAcl(
                new TenantId("2001"),
                "acl-003",
                scope,
                "user",
                "1001",
                permissionCode,
                "unknown",
                null,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("授权效果只能是 allow 或 deny");
    }

    @Test
    void rejectsDeniedDecisionWithoutReasonInsteadOfEmptyReasonFallback() {
        assertThatThrownBy(() -> new AuthorizationDecision(false, "permission_missing", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("拒绝原因不能为空");
    }

    @Test
    void rejectsAllowedDecisionWithoutExplicitReasonInsteadOfNullFallback() {
        assertThatThrownBy(() -> new AuthorizationDecision(true, "allowed", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("决策原因不能为空");
    }
}
