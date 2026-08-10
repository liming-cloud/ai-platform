package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.TenantCustomRoleBindingRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response.TenantCustomRoleBindingResponse;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 由 token 恢复的当前租户上下文执行成员自定义角色绑定。 */
@RestController
@RequestMapping("/api/v1/tenant/members")
public final class TenantCustomRoleBindingController {

    private final TenantCustomRoleBindingApplicationService bindings;

    public TenantCustomRoleBindingController(TenantCustomRoleBindingApplicationService bindings) {
        this.bindings = Objects.requireNonNull(bindings, "自定义角色绑定服务不能为空");
    }

    /**
     * 绑定当前租户的活动自定义角色，并撤销目标成员的旧租户会话。
     *
     * <p>租户、当前主体和会话一律从可信 token 上下文恢复；路径成员标识只是当前租户内的受管资源目标。</p>
     */
    @PostMapping("/{membershipId}/custom-role-bindings")
    public TenantCustomRoleBindingResponse bind(
            TrustedRequestContext context,
            @PathVariable String membershipId,
            @RequestBody Map<String, Object> payload) {
        var request = TenantCustomRoleBindingRequest.from(payload);
        final long memberId;
        try {
            memberId = Long.parseLong(membershipId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("成员标识必须是十进制正整数", exception);
        }
        return TenantCustomRoleBindingResponse.from(bindings.bind(context, request.toCommand(memberId)));
    }
}
