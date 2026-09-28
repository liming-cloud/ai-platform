package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.controller;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service.TenantFeatureSettingsService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request.TenantFeatureUpdateRequest;
import com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.response.TenantFeatureSettingsResponse;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台租户功能独立设置入口；身份只从平台会话恢复。 */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/features")
public final class TenantFeatureSettingsController {
    private final TenantFeatureSettingsService service;

    public TenantFeatureSettingsController(TenantFeatureSettingsService service) {
        this.service = Objects.requireNonNull(service, "功能配置服务不能为空");
    }

    /** 无权时在读取任何租户事实前拒绝。 */
    @GetMapping
    public TenantFeatureSettingsResponse get(PlatformRequestContext platform, @PathVariable long tenantId) {
        return TenantFeatureSettingsResponse.from(service.get(platform, tenantId));
    }

    /** 独立保存全部八项配置并递增版本；会话撤销失败时写入回滚。 */
    @PutMapping
    public TenantFeatureSettingsResponse update(PlatformRequestContext platform, @PathVariable long tenantId,
            @RequestBody Map<String, Object> payload) {
        var request = TenantFeatureUpdateRequest.from(payload);
        return TenantFeatureSettingsResponse.from(service.update(platform, tenantId,
                request.availableFeatures(), request.expectedVersion()));
    }
}
