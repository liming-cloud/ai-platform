package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleBindingRevocationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.TenantCustomRoleBindingRevocationApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.response.TenantCustomRoleBindingRevocationResponse;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 由 token 恢复的当前租户上下文解除成员自定义角色绑定。
 *
 * <p>租户、当前主体和会话不作为接口参数；两个路径标识只定位当前租户内的受管资源。</p>
 */
@RestController
@RequestMapping("/api/v1/tenant/members")
public final class TenantCustomRoleBindingRevocationController {

    private final TenantCustomRoleBindingRevocationApplicationService revocations;

    public TenantCustomRoleBindingRevocationController(
            TenantCustomRoleBindingRevocationApplicationService revocations) {
        this.revocations = Objects.requireNonNull(revocations, "自定义角色解绑服务不能为空");
    }

    /** 解除当前成员的一项活动自定义角色绑定，并使其旧租户会话失效。 */
    @DeleteMapping("/{membershipId}/custom-role-bindings/{customRoleId}")
    public TenantCustomRoleBindingRevocationResponse unbind(
            TrustedRequestContext context,
            @PathVariable String membershipId,
            @PathVariable String customRoleId) {
        return TenantCustomRoleBindingRevocationResponse.from(revocations.unbind(
                context,
                new TenantCustomRoleBindingRevocationCommand(
                        parsePositiveIdentifier(membershipId, "成员"),
                        parsePositiveIdentifier(customRoleId, "自定义角色"))));
    }

    private static long parsePositiveIdentifier(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0L) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + "标识必须是十进制正整数", exception);
        }
    }
}
