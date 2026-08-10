package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantRoleBindingCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.TenantRoleBindingRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response.TenantRoleBindingResponse;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 由 token 恢复的当前租户上下文执行成员内置角色绑定。
 *
 * <p>租户、当前主体和会话一律不作为接口参数；路径成员标识只是当前租户内的受管资源目标。</p>
 */
@RestController
@RequestMapping("/api/v1/tenant/members")
public final class TenantRoleBindingController {

    private final TenantRoleBindingApplicationService bindings;

    public TenantRoleBindingController(TenantRoleBindingApplicationService bindings) {
        this.bindings = Objects.requireNonNull(bindings, "角色绑定服务不能为空");
    }

    /**
     * 绑定当前已发布的租户内置角色，并撤销目标成员的旧租户会话。
     */
    @PostMapping("/{membershipId}/role-bindings")
    public TenantRoleBindingResponse bind(
            TrustedRequestContext context,
            @PathVariable String membershipId,
            @RequestBody Map<String, Object> payload) {
        var request = TenantRoleBindingRequest.from(payload);
        final long memberId;
        try {
            memberId = Long.parseLong(membershipId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("成员标识必须是十进制正整数", exception);
        }
        return TenantRoleBindingResponse.from(bindings.bind(
                context,
                new TenantRoleBindingCommand(memberId, request.roleCode())));
    }
}
