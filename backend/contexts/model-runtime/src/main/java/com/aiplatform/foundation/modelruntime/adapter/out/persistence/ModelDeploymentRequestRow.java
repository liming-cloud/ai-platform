package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

/**
 * 模型部署幂等请求行。
 */
public class ModelDeploymentRequestRow {

    private String requestFingerprint;
    private String modelDeploymentId;

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public String getModelDeploymentId() {
        return modelDeploymentId;
    }

    public void setModelDeploymentId(String modelDeploymentId) {
        this.modelDeploymentId = modelDeploymentId;
    }
}
