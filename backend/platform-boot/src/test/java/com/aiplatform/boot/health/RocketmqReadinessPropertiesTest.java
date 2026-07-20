package com.aiplatform.boot.health;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RocketmqReadinessPropertiesTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsTypedBoundedEndpointSetsAndTimeout() {
        RocketmqReadinessProperties properties = validProperties();

        assertThat(validator.validate(properties)).isEmpty();
        assertThat(properties.getNameServers()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.getHost()).isEqualTo("namesrv");
            assertThat(endpoint.getPort()).isEqualTo(9876);
        });
        assertThat(properties.getBrokers()).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.getHost()).isEqualTo("broker");
            assertThat(endpoint.getPort()).isEqualTo(10911);
        });
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void rejectsEmptyOrOversizedEndpointSetsInvalidEndpointAndUnsafeTimeout() {
        RocketmqReadinessProperties properties = validProperties();
        properties.setNameServers(List.of());
        List<RocketmqReadinessEndpoint> brokers = new ArrayList<>(IntStream.range(0, 5)
                .mapToObj(index -> endpoint("broker-" + index, 10911))
                .toList());
        properties.setConnectTimeout(Duration.ofSeconds(2));
        RocketmqReadinessEndpoint invalid = endpoint(" ", 0);
        brokers.set(0, invalid);
        properties.setBrokers(brokers);

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "nameServers",
                        "brokers",
                        "brokers[0].host",
                        "brokers[0].port",
                        "connectTimeout"
                );
    }

    private static RocketmqReadinessProperties validProperties() {
        RocketmqReadinessProperties properties = new RocketmqReadinessProperties();
        properties.setNameServers(List.of(endpoint("namesrv", 9876)));
        properties.setBrokers(List.of(endpoint("broker", 10911)));
        properties.setConnectTimeout(Duration.ofMillis(250));
        return properties;
    }

    private static RocketmqReadinessEndpoint endpoint(String host, int port) {
        RocketmqReadinessEndpoint endpoint = new RocketmqReadinessEndpoint();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }
}
