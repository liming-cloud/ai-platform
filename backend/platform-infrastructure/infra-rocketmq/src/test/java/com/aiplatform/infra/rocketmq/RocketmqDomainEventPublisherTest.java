package com.aiplatform.infra.rocketmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.cloud.stream.binder.rocketmq.support.RocketMQMessageConverterSupport;
import com.aiplatform.infra.rocketmq.config.RocketmqDomainEventProperties;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.infra.rocketmq.publisher.RocketmqDomainEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class RocketmqDomainEventPublisherTest {
    @Test
    void sendsExactJsonBytesBySpringCloudStreamBinding() {
        RecordingDomainEventStreamGateway streamGateway = new RecordingDomainEventStreamGateway();
        RocketmqDomainEventProperties properties = new RocketmqDomainEventProperties();
        RocketmqDomainEventPublisher publisher = new RocketmqDomainEventPublisher(
                streamGateway,
                properties,
                brokerMapper()
        );
        DomainEventMessage message = new DomainEventMessage(
                "msg-1",
                "DocumentIngestRequested",
                "2001",
                "KnowledgeDocument",
                "8001",
                1,
                "v1",
                Instant.parse("2026-05-22T08:00:00.123456Z"),
                "TRACE-KB-DOC-001",
                "knowledge-base",
                "{\"documentId\":\"8001\"}"
        );

        publisher.publish(message);

        assertThat(streamGateway.bindingName).isEqualTo("domainEvent-out-0");
        assertThat(new String(streamGateway.message, StandardCharsets.UTF_8))
                .contains("\"occurredAt\":\"2026-05-22T08:00:00.123456Z\"");
    }

    @Test
    void rocketMqBinderKeepsPreEncodedDomainEventBytesUnchanged() {
        byte[] wireMessage = "{\"occurredAt\":\"2026-05-22T08:00:00.123456Z\"}"
                .getBytes(StandardCharsets.UTF_8);

        byte[] brokerBody = RocketMQMessageConverterSupport.convertMessage2MQ(
                "topic-ai-platform-domain-event",
                MessageBuilder.withPayload(wireMessage).build()
        ).getBody();

        assertThat(brokerBody).containsExactly(wireMessage);
    }

    @Test
    void rejectsABrokerGatewaySendThatWasNotAccepted() {
        RocketmqDomainEventProperties properties = new RocketmqDomainEventProperties();
        RocketmqDomainEventPublisher publisher = new RocketmqDomainEventPublisher(
                (bindingName, message) -> false,
                properties,
                brokerMapper()
        );

        assertThatThrownBy(() -> publisher.publish(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("领域事件发送未被消息网关接受");
    }

    private static DomainEventMessage message() {
        return new DomainEventMessage(
                "msg-rejected",
                "ModelUsagePolicyPublished",
                "2001",
                "ModelConfig",
                "4001",
                1,
                "v1",
                Instant.parse("2026-07-17T08:00:00Z"),
                "trace-rejected",
                "model-hub",
                "{\"modelConfigId\":\"4001\"}"
        );
    }

    private static JsonMapper brokerMapper() {
        return JsonMapper.builder()
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DateTimeFeature.TRUNCATE_TO_MSECS_ON_WRITE)
                .build();
    }
}
