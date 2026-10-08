package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantWorkspaceContextView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantWorkspaceContextQueryService;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 当前租户管理员的外壳上下文；范围只由服务器验证的会话决定。 */
@RestController
public final class TenantWorkspaceContextController {
    private final TenantWorkspaceContextQueryService service;

    /** 注册只读上下文入口。 */
    public TenantWorkspaceContextController(TenantWorkspaceContextQueryService service) {
        this.service = Objects.requireNonNull(service);
    }

    /** 拒绝任何外部范围参数，响应只包含真实租户名称。 */
    @GetMapping("/api/v1/tenant/workspace-context")
    public TenantWorkspaceContextView get(TrustedRequestContext context, @RequestParam Map<String, String> parameters) {
        if (!parameters.isEmpty()) throw new IllegalArgumentException("工作区上下文不接受查询参数");
        return service.get(context);
    }
}
