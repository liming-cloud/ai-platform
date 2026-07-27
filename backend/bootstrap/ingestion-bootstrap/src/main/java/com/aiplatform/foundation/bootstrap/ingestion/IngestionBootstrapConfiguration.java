package com.aiplatform.foundation.bootstrap.ingestion;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 索引摄取进程的唯一跨上下文装配入口。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class IngestionBootstrapConfiguration {
}
