package com.aiplatform.infra.rocketmq.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@ConfigurationProperties(prefix = "aip.rocketmq.domain-event")
public class RocketmqDomainEventProperties {
    @NotBlank
    private String bindingName = "domainEvent-out-0";

    public String getBindingName() {
        return bindingName;
    }

    public void setBindingName(String bindingName) {
        this.bindingName = bindingName;
    }
}
