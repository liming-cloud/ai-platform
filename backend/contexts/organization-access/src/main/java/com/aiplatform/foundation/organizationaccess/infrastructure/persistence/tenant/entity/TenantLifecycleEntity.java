package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 生命周期权威事实的单表映射。 */
@TableName("organization_access.tenant_lifecycle")
public record TenantLifecycleEntity(@TableId(type = IdType.INPUT) Long tenantId, String tenantCode, String displayName, Long initialAdministratorSubjectId, String status, Long version, Instant createdAt) { }
