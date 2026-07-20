package com.aiplatform.infra.rocketmq.publisher.gateway;

import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

/**
 * Spring Cloud Stream 网关适配器，统一把领域事件发送到配置化 binding。
 */
@Component
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.domainEvent-out-0.destination")
public class SpringCloudStreamDomainEventStreamGateway implements DomainEventStreamGateway {
    private final StreamBridge streamBridge;

    public SpringCloudStreamDomainEventStreamGateway(StreamBridge streamBridge) {
        this.streamBridge = Objects.requireNonNull(streamBridge, "Spring Cloud Stream 网桥不能为空");
    }

    @Override
    public boolean send(String bindingName, byte[] wireMessage) {
        return streamBridge.send(bindingName, wireMessage);
    }
}
