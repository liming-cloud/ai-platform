package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.projection;

/**
 * 模型部署幂等请求行。
 */
public class ModelDeploymentRequestRow {

    private String requestFingerprint;
    private Long modelDeploymentId;

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Long getModelDeploymentId() {
        return modelDeploymentId;
    }

    public void setModelDeploymentId(Long modelDeploymentId) {
        this.modelDeploymentId = modelDeploymentId;
    }
}
