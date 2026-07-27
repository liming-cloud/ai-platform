package com.aiplatform.foundation.trustedquery.domain.query.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 查询事实中冻结的请求主体标识。
 */
public record QueryRequesterId(long value) implements ValueObject {

    public QueryRequesterId {
        if (value <= 0) {
            throw new IllegalArgumentException("查询请求主体标识必须是正整数");
        }
    }
}
