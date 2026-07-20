package com.aiplatform.shared.kernel;

/**
 * 聚合根基础接口，用于标识事务一致性边界的根对象。
 *
 * @param <ID> 聚合根身份类型
 */
public interface AggregateRoot<ID> {
    ID id();
}
