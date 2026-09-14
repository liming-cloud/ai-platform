package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import java.time.Instant;

/** 固定初始管理员角色绑定的单表写入映射。 */
public record TenantAdministratorRoleEntity(Long tenantId, Long membershipId, String roleCode, Instant boundAt) { }
