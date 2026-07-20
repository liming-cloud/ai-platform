package com.aiplatform.infra.rocketmq.publisher;

import com.aiplatform.infra.rocketmq.config.RocketmqDomainEventProperties;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.gateway.DomainEventStreamGateway;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 基于 Spring Cloud Stream 的领域事件发布器，底层 binder 可切换为 RocketMQ 或其他消息实现。
 */
@Component
@ConditionalOnProperty(name = "spring.cloud.stream.bindings.domainEvent-out-0.destination")
public class RocketmqDomainEventPublisher implements DomainEventPublisher {
    private final DomainEventStreamGateway streamGateway;
    private final RocketmqDomainEventProperties properties;
    private final JsonMapper objectMapper;

    public RocketmqDomainEventPublisher(
            DomainEventStreamGateway streamGateway,
            RocketmqDomainEventProperties properties,
            JsonMapper objectMapper
    ) {
        this.streamGateway = Objects.requireNonNull(streamGateway, "领域事件消息网关不能为空");
        this.properties = Objects.requireNonNull(properties, "领域事件 RocketMQ 配置不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "领域事件 JSON 序列化器不能为空");
    }

    @Override
    public void publish(DomainEventMessage message) {
        Objects.requireNonNull(message, "领域事件消息不能为空");
        if (!streamGateway.send(properties.getBindingName(), wireBytes(message))) {
            throw new IllegalStateException("领域事件发送未被消息网关接受");
        }
    }

    private byte[] wireBytes(DomainEventMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JacksonException exception) {
            throw new IllegalStateException("领域事件线协议序列化失败", exception);
        }
    }
}
