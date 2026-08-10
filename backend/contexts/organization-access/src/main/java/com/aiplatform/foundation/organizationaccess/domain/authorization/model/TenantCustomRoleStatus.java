package com.aiplatform.foundation.organizationaccess.domain.authorization.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/** 租户自定义角色是否可用于新的成员绑定。 */
public enum TenantCustomRoleStatus implements ValueObject {
    ACTIVE,
    DISABLED
}
