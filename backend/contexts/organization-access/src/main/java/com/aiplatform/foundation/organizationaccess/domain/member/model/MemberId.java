package com.aiplatform.foundation.organizationaccess.domain.member.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 主体在一个租户内的成员身份标识。
 */
public record MemberId(long value) implements ValueObject {

    public MemberId {
        if (value <= 0L) {
            throw new IllegalArgumentException("成员标识必须为正数");
        }
    }
}
