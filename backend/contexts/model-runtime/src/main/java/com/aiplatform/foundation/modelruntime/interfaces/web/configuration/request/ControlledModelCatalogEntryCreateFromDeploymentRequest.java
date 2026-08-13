package com.aiplatform.foundation.modelruntime.interfaces.web.configuration.request;

/** 平台目录创建只引用已发布部署的稳定标识和乐观修订。 */
public record ControlledModelCatalogEntryCreateFromDeploymentRequest(
        long platformDeploymentId, long expectedDeploymentVersion) {

    public ControlledModelCatalogEntryCreateFromDeploymentRequest {
        if (platformDeploymentId <= 0 || expectedDeploymentVersion < 0) {
            throw new IllegalArgumentException("平台部署引用格式不正确");
        }
    }
}
