package com.aiplatform.boot.health;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RocketMQ readiness 使用的单个只读管理协议端点配置。
 */
public final class RocketmqReadinessEndpoint {
    @NotBlank
    @Size(max = 253)
    private String host;

    @Min(1)
    @Max(65_535)
    private int port;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host == null ? null : host.trim();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
