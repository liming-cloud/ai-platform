package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

/**
 * 已登记供应商连接创建请求的最小事实。
 */
public class ProviderConnectionRequestRow {

    private String requestFingerprint;
    private String providerConnectionId;

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public String getProviderConnectionId() {
        return providerConnectionId;
    }

    public void setProviderConnectionId(String providerConnectionId) {
        this.providerConnectionId = providerConnectionId;
    }
}
