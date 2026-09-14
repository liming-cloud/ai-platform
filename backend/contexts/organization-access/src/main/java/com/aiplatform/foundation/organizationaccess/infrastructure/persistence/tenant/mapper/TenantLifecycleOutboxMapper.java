package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLifecycleOutboxEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 不含凭据、成员或正文的最小生命周期事件映射。 */
public interface TenantLifecycleOutboxMapper extends BaseMapper<TenantLifecycleOutboxEntity> { }
