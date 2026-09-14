package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantLifecycleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 生命周期单表读写与全局认证就绪断言，不暴露候选主体旧路径。 */
public interface TenantProvisioningMapper extends BaseMapper<TenantLifecycleEntity> {
    /** 检查受控迁移持久化的全局切换事实；开户不得创建该事实。 */
    boolean authenticationReady();
}
