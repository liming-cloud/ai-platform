package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.projection;

/**
 * 已登记供应商连接创建请求的最小事实。
 */
public class ProviderConnectionRequestRow {

    private String requestFingerprint;
    private Long providerConnectionId;

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Long getProviderConnectionId() {
        return providerConnectionId;
    }

    public void setProviderConnectionId(Long providerConnectionId) {
        this.providerConnectionId = providerConnectionId;
    }
}
