package com.aiplatform.infra.rocketmq.publisher.gateway;

/**
 * 领域事件流发送网关，隔离 Spring Cloud Stream final 类型，保证发布器测试不依赖 JVM 动态 agent。
 */
public interface DomainEventStreamGateway {
    boolean send(String bindingName, byte[] wireMessage);
}
