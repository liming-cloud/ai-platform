package com.aiplatform.infra.rocketmq.publisher;

import com.aiplatform.infra.rocketmq.model.DomainEventMessage;

public interface DomainEventPublisher {
    void publish(DomainEventMessage message);
}
