package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

import tools.jackson.databind.JsonNode;

/** 精确部署与租户授权的乐观修订，不接受缺失、字符串、小数或溢出值。 */
public record PlatformSupplyRevokeRequest(JsonNode expectedVersion, JsonNode expectedGrantVersion) {
    public PlatformSupplyRevokeRequest {
        version(expectedVersion); version(expectedGrantVersion);
    }

    /** 返回服务端可比较的部署修订。 */
    public long deploymentVersion() { return version(expectedVersion); }
    /** 返回独立供应授权修订，与部署修订不能互换。 */
    public long grantVersion() { return version(expectedGrantVersion); }

    private static long version(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw new IllegalArgumentException("必须提供非负整数修订");
        return value.longValue();
    }
}
