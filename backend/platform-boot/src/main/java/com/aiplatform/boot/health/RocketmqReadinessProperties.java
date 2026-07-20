package com.aiplatform.boot.health;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * RocketMQ 主动就绪探针配置。限制端点数量与单次连接、响应超时，约束健康检查的网络工作量。
 */
@Validated
@Component
@ConfigurationProperties(prefix = "aip.rocketmq.readiness")
public class RocketmqReadinessProperties {
    private static final int MAX_ENDPOINTS_PER_SET = 4;

    @Valid
    @Size(min = 1, max = MAX_ENDPOINTS_PER_SET)
    private List<@NotNull RocketmqReadinessEndpoint> nameServers = new ArrayList<>();

    @Valid
    @Size(min = 1, max = MAX_ENDPOINTS_PER_SET)
    private List<@NotNull RocketmqReadinessEndpoint> brokers = new ArrayList<>();

    @NotNull
    @DurationMin(millis = 10)
    @DurationMax(seconds = 1)
    private Duration connectTimeout = Duration.ofMillis(250);

    public List<RocketmqReadinessEndpoint> getNameServers() {
        return nameServers;
    }

    public void setNameServers(List<RocketmqReadinessEndpoint> nameServers) {
        this.nameServers = nameServers == null ? null : new ArrayList<>(nameServers);
    }

    public List<RocketmqReadinessEndpoint> getBrokers() {
        return brokers;
    }

    public void setBrokers(List<RocketmqReadinessEndpoint> brokers) {
        this.brokers = brokers == null ? null : new ArrayList<>(brokers);
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
