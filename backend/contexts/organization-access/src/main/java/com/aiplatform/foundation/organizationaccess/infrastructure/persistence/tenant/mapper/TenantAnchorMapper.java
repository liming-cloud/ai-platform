package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantAnchorEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 仅供跨上下文外键使用的租户标识锚点，不作为新开户的认证状态源。 */
public interface TenantAnchorMapper extends BaseMapper<TenantAnchorEntity> { }
