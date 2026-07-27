package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.projection;

/**
 * 助手幂等请求数据库行。
 */
public final class AssistantRequestRow {

    private String requestFingerprint;
    private String assistantId;
    private String assistantVersionId;

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String value) {
        requestFingerprint = value;
    }

    public String getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(String value) {
        assistantId = value;
    }

    public String getAssistantVersionId() {
        return assistantVersionId;
    }

    public void setAssistantVersionId(String value) {
        assistantVersionId = value;
    }
}
