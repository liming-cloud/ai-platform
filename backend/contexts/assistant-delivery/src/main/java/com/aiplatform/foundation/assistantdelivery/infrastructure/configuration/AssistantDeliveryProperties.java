package com.aiplatform.foundation.assistantdelivery.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 助手交付能力的配额配置。
 */
@ConfigurationProperties("foundation.assistant-delivery")
public class AssistantDeliveryProperties {

    private int assistantLimit = 5;
    private int versionLimit = 20;

    public int getAssistantLimit() {
        return assistantLimit;
    }

    public void setAssistantLimit(int assistantLimit) {
        this.assistantLimit = requirePositive(assistantLimit, "助手配额");
    }

    public int getVersionLimit() {
        return versionLimit;
    }

    public void setVersionLimit(int versionLimit) {
        this.versionLimit = requirePositive(versionLimit, "助手版本配额");
    }

    private static int requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + "必须大于零");
        }
        return value;
    }
}
