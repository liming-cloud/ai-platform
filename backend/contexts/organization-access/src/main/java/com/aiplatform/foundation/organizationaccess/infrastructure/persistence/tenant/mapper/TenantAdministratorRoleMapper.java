package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.entity.TenantAdministratorRoleEntity;

/** 角色绑定具有复合主键，使用明确 XML 写入，禁止伪造 MyBatis-Plus 单字段主键。 */
public interface TenantAdministratorRoleMapper {
    /** 保存领域决定的固定管理员角色绑定。 */
    int insert(TenantAdministratorRoleEntity row);
}
