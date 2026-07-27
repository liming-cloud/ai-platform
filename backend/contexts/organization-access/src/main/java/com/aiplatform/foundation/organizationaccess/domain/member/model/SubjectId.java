package com.aiplatform.foundation.organizationaccess.domain.member.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 人员、服务或设备主体的组织领域标识。
 */
public record SubjectId(long value) implements ValueObject {

    public SubjectId {
        if (value <= 0L) {
            throw new IllegalArgumentException("主体标识必须为正数");
        }
    }
}
