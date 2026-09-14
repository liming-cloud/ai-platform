package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantQuotaEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 租户配额的单表读写由 MyBatis-Plus 承担，事务与授权由调用用例负责。 */
public interface TenantQuotaMapper extends BaseMapper<TenantQuotaEntity> {
}
