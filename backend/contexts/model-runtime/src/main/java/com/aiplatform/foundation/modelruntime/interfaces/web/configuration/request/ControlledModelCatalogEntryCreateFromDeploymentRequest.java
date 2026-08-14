package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request;

/** 平台目录创建只引用已发布部署的稳定标识和乐观修订。 */
public record ControlledModelCatalogEntryCreateFromDeploymentRequest(
        String platformDeploymentId, long expectedDeploymentVersion) {

    public ControlledModelCatalogEntryCreateFromDeploymentRequest {
        if (parsePlatformDeploymentId(platformDeploymentId) <= 0 || expectedDeploymentVersion < 0) {
            throw new IllegalArgumentException("平台部署引用格式不正确");
        }
    }

    /** 64 位标识在浏览器契约中保持字符串，避免 JavaScript 数值精度损失。 */
    public long parsedPlatformDeploymentId() {
        return parsePlatformDeploymentId(platformDeploymentId);
    }

    private static long parsePlatformDeploymentId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("平台部署引用格式不正确");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("平台部署引用格式不正确", exception);
        }
    }
}
