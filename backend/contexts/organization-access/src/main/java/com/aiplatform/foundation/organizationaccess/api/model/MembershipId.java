package com.aiplatform.foundation.organizationaccess.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 主体在一个租户内的成员身份标识。
 */
public record MembershipId(UUID value) {

    public MembershipId {
        Objects.requireNonNull(value, "成员标识不能为空");
    }
}
