package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.controller;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformRoleBindingView;
import com.aiplatform.foundation.organizationaccess.application.authorization.service.PlatformRoleBindingApplicationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request.PlatformRoleBindingRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 由派生平台上下文管理目标账户的固定角色绑定。 */
@RestController
@RequestMapping("/api/v1/platform/accounts")
public final class PlatformRoleBindingController {

    private final PlatformRoleBindingApplicationService bindings;

    /** 构造平台角色绑定 HTTP 入口。 */
    public PlatformRoleBindingController(PlatformRoleBindingApplicationService bindings) {
        this.bindings = Objects.requireNonNull(bindings, "平台角色绑定服务不能为空");
    }

    /** 绑定路径目标账户的一项固定平台角色。 */
    @PostMapping("/{subjectId}/role-bindings")
    public PlatformRoleBindingView bind(
            PlatformRequestContext platform,
            @PathVariable String subjectId,
            @RequestBody Map<String, Object> payload) {
        return bindings.bind(platform, parseSubjectId(subjectId), PlatformRoleBindingRequest.from(payload).roleCode());
    }

    /** 撤销路径目标账户的一项固定平台角色。 */
    @DeleteMapping("/{subjectId}/role-bindings/{roleCode}")
    public PlatformRoleBindingView revoke(
            PlatformRequestContext platform,
            @PathVariable String subjectId,
            @PathVariable String roleCode) {
        return bindings.revoke(platform, parseSubjectId(subjectId), roleCode);
    }

    private static long parseSubjectId(String subjectId) {
        try {
            return Long.parseLong(subjectId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("主体标识必须是十进制正整数", exception);
        }
    }
}
