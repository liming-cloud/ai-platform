package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.dto.TenantQuotaView;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantQuotaSettingsService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantQuotaUpdateRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response.TenantQuotaUsageResponse;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台租户配额独立设置入口；身份只从受信请求上下文恢复。 */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/quotas")
@ConditionalOnProperty(name = "foundation.knowledge-data.persistence.enabled", havingValue = "true")
public final class TenantQuotaSettingsController {

    private final TenantQuotaSettingsService service;

    public TenantQuotaSettingsController(TenantQuotaSettingsService service) {
        this.service = Objects.requireNonNull(service, "配额设置服务不能为空");
    }

    /** 读取当前已核实的三个资源下限。 */
    @GetMapping("/usage")
    public TenantQuotaUsageResponse usage(PlatformRequestContext platform, @PathVariable long tenantId) {
        return TenantQuotaUsageResponse.from(service.usage(platform, tenantId));
    }

    /** 使用预期版本保存五项配置；未知字段不能参与授权或写入。 */
    @PutMapping
    public TenantQuotaView update(PlatformRequestContext platform, @PathVariable long tenantId,
            @RequestBody Map<String, Object> payload) {
        return service.update(platform, tenantId, TenantQuotaUpdateRequest.from(payload));
    }
}
