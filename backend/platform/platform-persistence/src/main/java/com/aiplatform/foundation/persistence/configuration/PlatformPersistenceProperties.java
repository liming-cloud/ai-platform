package com.aiplatform.foundation.persistence.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台持久化的雪花节点配置。
 */
@ConfigurationProperties("foundation.persistence.snowflake")
public record PlatformPersistenceProperties(
        Long workerId,
        Long datacenterId) {

    public PlatformPersistenceProperties {
        if (workerId == null || datacenterId == null) {
            throw new IllegalArgumentException("雪花工作节点和数据中心编号必须显式配置");
        }
    }
}
