package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/** 不含凭据、成员或正文的最小生命周期事件映射。 */
@TableName("organization_access.tenant_lifecycle_outbox")
public record TenantLifecycleOutboxEntity(@TableId(type = IdType.INPUT) Long eventId, Long tenantId, String eventType, String lifecycleStatus, Long lifecycleVersion, Instant occurredAt) { }
