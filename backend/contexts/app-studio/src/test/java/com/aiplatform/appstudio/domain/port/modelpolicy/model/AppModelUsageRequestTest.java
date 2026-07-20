package com.aiplatform.appstudio.domain.port.modelpolicy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppModelUsageRequestTest {
    @Test
    void normalizesModelUsageRequestFacts() {
        AppModelUsageRequest request = new AppModelUsageRequest(
                TenantId.of("2001"),
                " user-1 ",
                " user ",
                Set.of(" platform-admin "),
                Set.of(new PermissionCode("app-studio:model:use")),
                " app-1 ",
                " chat ",
                " chat ",
                " 9001 ",
                Map.of("temperature", 0.2),
                128,
                512
        );

        assertThat(request.actorId()).isEqualTo("user-1");
        assertThat(request.actorType()).isEqualTo("user");
        assertThat(request.roleCodes()).containsExactly("platform-admin");
        assertThat(request.applicationId()).isEqualTo("app-1");
        assertThat(request.useCase()).isEqualTo("chat");
        assertThat(request.requiredCapability()).isEqualTo("chat");
        assertThat(request.requestedModelConfigId()).isEqualTo("9001");
    }

    @Test
    void rejectsBlankRoleCodeInsteadOfDroppingIt() {
        assertThatThrownBy(() -> new AppModelUsageRequest(
                TenantId.of("2001"),
                "user-1",
                "user",
                Set.of(" "),
                Set.of(new PermissionCode("app-studio:model:use")),
                "app-1",
                "chat",
                "chat",
                "9001",
                Map.of("temperature", 0.2),
                128,
                512
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("角色编码不能为空");
    }

    @Test
    void allowsPermissionOnlyPrincipalWithoutAssignedRole() {
        AppModelUsageRequest request = new AppModelUsageRequest(
                TenantId.of("2001"),
                "user-1",
                "user",
                Set.of(),
                Set.of(new PermissionCode("app-studio:model:use")),
                "app-1",
                "chat",
                "chat",
                "9001",
                Map.of(),
                0,
                0
        );

        assertThat(request.roleCodes()).isEmpty();
    }

    @Test
    void rejectsNullPermissionOrRequestedOptionInsteadOfJdkNullPointerException() {
        Set<PermissionCode> permissions = new HashSet<>();
        permissions.add(null);

        assertThatThrownBy(() -> new AppModelUsageRequest(
                TenantId.of("2001"),
                "user-1",
                "user",
                Set.of("platform-admin"),
                permissions,
                "app-1",
                "chat",
                "chat",
                "9001",
                Map.of("temperature", 0.2),
                128,
                512
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("权限快照不能为空");

        Map<String, Object> requestedOptions = new HashMap<>();
        requestedOptions.put("temperature", null);

        assertThatThrownBy(() -> new AppModelUsageRequest(
                TenantId.of("2001"),
                "user-1",
                "user",
                Set.of("platform-admin"),
                Set.of(new PermissionCode("app-studio:model:use")),
                "app-1",
                "chat",
                "chat",
                "9001",
                requestedOptions,
                128,
                512
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型 options value不能为空");
    }
}
