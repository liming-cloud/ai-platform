package com.aiplatform.foundation.shared.kernel.domain;

/**
 * 标记具有稳定领域身份的对象。
 *
 * @param <ID> 实体标识值对象类型
 */
public interface Entity<ID extends ValueObject> extends DomainObject {

    /**
     * 返回实体在所属边界内的稳定标识。
     *
     * @return 非空实体标识
     */
    ID id();
}
