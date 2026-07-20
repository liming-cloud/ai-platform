package com.aiplatform.infra.rocketmq;

import com.aiplatform.infra.rocketmq.publisher.gateway.DomainEventStreamGateway;

final class RecordingDomainEventStreamGateway implements DomainEventStreamGateway {
    String bindingName;
    byte[] message;

    @Override
    public boolean send(String bindingName, byte[] message) {
        this.bindingName = bindingName;
        this.message = message;
        return true;
    }
}
