package com.aiplatform.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class DomainEventStreamConfigurationTest {
    private static final List<String> DOMAIN_EVENT_CONSUMER_BINDINGS = List.of(
            "modelConfigApprovalRequested-in-0",
            "approvalDecisionRecorded-in-0",
            "modelUsagePolicyPublished-in-0",
            "appStudioModelUsagePolicyPublished-in-0",
            "documentIngestRequested-in-0"
    );

    @Test
    void domainEventProducerUsesNativeEncodingForPreSerializedWireBytes() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty(
                "spring.cloud.stream.bindings.domainEvent-out-0.producer.use-native-encoding"
        )).isEqualTo("true");
    }

    @Test
    void rocketmqConsumersDelegateFailedDeliveryToBrokerNativeRetry() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        for (String binding : DOMAIN_EVENT_CONSUMER_BINDINGS) {
            assertThat(properties.getProperty(
                    "spring.cloud.stream.bindings." + binding + ".consumer.max-attempts"
            )).as(binding + " must not allow in-process retry overrides").isEqualTo("1");
        }
    }

    @Test
    void rocketmqConsumersUseBoundedBrokerNativeRetryWithExplicitDelay() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        for (String binding : DOMAIN_EVENT_CONSUMER_BINDINGS) {
            String prefix = "spring.cloud.stream.rocketmq.bindings."
                    + binding + ".consumer.push.";
            assertThat(properties.getProperty(prefix + "max-reconsume-times"))
                    .as(binding + " max broker reconsume attempts")
                    .isEqualTo("${AIP_ROCKETMQ_CONSUMER_MAX_RECONSUME_TIMES:5}");
            assertThat(properties.getProperty(prefix + "delay-level-when-next-consume"))
                    .as(binding + " retry delay level")
                    .isEqualTo("${AIP_ROCKETMQ_CONSUMER_DELAY_LEVEL_WHEN_NEXT_CONSUME:3}");
        }
    }
}
