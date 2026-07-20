package com.aiplatform.shared.kernel;

/**
 * 领域实体基础接口，用于标识具备生命周期和稳定身份的领域对象。
 *
 * @param <ID> 实体身份类型
 */
public interface DomainEntity<ID> {
    ID id();
}
