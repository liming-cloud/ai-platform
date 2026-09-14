package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 仅供跨上下文外键使用的租户标识锚点，不作为新开户的认证状态源。 */
@TableName("organization_access.tenant")
public record TenantAnchorEntity(@TableId(type = IdType.INPUT) Long tenantId, String tenantCode, String displayName, String status, Long version, Instant createdAt, Instant updatedAt) { }
