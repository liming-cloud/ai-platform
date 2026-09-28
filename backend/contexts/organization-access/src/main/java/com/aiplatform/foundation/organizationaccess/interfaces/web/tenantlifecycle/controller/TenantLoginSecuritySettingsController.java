package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantLoginSecurityPolicyResult;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantLoginSecuritySettingsService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantLoginSecurityUpdateRequest;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台租户登录策略的独立入口；身份只从平台会话恢复。 */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/security")
public final class TenantLoginSecuritySettingsController {
    private final TenantLoginSecuritySettingsService service;

    public TenantLoginSecuritySettingsController(TenantLoginSecuritySettingsService service) {
        this.service = Objects.requireNonNull(service, "租户登录策略服务不能为空");
    }

    /** 由服务层在读取目标事实前核验平台治理权限。 */
    @GetMapping
    public TenantLoginSecurityPolicyResult get(PlatformRequestContext platform, @PathVariable long tenantId) {
        return service.get(platform, tenantId);
    }

    /** 完整替换三项值，独立保存并按目标租户立即撤销旧会话。 */
    @PutMapping
    public TenantLoginSecurityPolicyResult update(PlatformRequestContext platform, @PathVariable long tenantId,
            @RequestBody Map<String, Object> payload) {
        var request = TenantLoginSecurityUpdateRequest.from(payload);
        return service.update(platform, tenantId, request.sessionHours(), request.failedAttempts(),
                request.lockMinutes(), request.expectedVersion());
    }
}
