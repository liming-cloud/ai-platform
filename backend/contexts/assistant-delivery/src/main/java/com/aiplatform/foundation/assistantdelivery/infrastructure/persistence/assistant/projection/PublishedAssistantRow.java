package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.projection;

import java.time.Instant;

/**
 * 已发布助手目录的数据库读取行。
 */
public final class PublishedAssistantRow {

    private String assistantId;
    private String displayName;
    private String assistantVersionId;
    private int versionNumber;
    private Instant publishedAt;

    public String getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(String value) {
        assistantId = value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        displayName = value;
    }

    public String getAssistantVersionId() {
        return assistantVersionId;
    }

    public void setAssistantVersionId(String value) {
        assistantVersionId = value;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int value) {
        versionNumber = value;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant value) {
        publishedAt = value;
    }
}
