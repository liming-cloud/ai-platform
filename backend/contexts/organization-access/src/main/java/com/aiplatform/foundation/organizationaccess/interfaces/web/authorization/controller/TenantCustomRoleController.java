package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.TenantCustomRoleCreationRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response.TenantCustomRoleResponse;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 由 token 恢复的当前租户上下文创建自定义角色。 */
@RestController
@RequestMapping("/api/v1/tenant/custom-roles")
public final class TenantCustomRoleController {

    private final TenantCustomRoleApplicationService roles;

    public TenantCustomRoleController(TenantCustomRoleApplicationService roles) {
        this.roles = Objects.requireNonNull(roles, "租户自定义角色服务不能为空");
    }

    /**
     * 创建当前租户拥有的自定义角色。
     *
     * <p>租户、当前主体、权限版本和会话均只从可信 token 上下文恢复，不能由请求体伪造。</p>
     */
    @PostMapping
    public ResponseEntity<TenantCustomRoleResponse> create(
            TrustedRequestContext context,
            @RequestBody Map<String, Object> payload) {
        var request = TenantCustomRoleCreationRequest.from(payload);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TenantCustomRoleResponse.from(roles.create(context, request.toCommand())));
    }
}
