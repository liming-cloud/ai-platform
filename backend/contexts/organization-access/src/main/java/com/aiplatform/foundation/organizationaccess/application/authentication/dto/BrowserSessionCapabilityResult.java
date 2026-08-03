package com.aiplatform.foundation.organizationaccess.application.authentication.dto;

import java.util.Objects;
import java.util.Optional;

/**
 * 浏览器能力投影与需要清除的失效范围会话，不携带 token。
 */
public record BrowserSessionCapabilityResult(
        Optional<BrowserSessionCapability> capability,
        boolean clearTenantSession,
        boolean clearPlatformSession) {

    public BrowserSessionCapabilityResult {
        capability = Objects.requireNonNull(capability, "浏览器能力投影不能为空");
    }
}
