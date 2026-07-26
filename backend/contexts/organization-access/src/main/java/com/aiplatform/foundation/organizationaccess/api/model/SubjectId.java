package com.aiplatform.foundation.organizationaccess.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 人员、服务或设备主体的不可变内部标识。
 */
public record SubjectId(UUID value) {

    public SubjectId {
        Objects.requireNonNull(value, "主体标识不能为空");
    }
}
