package com.aiplatform.foundation.shared.kernel.domain;

/**
 * 标记聚合的唯一外部访问入口和事务一致性边界。
 *
 * @param <ID> 聚合根标识值对象类型
 */
public interface AggregateRoot<ID extends ValueObject> extends Entity<ID> {
}
