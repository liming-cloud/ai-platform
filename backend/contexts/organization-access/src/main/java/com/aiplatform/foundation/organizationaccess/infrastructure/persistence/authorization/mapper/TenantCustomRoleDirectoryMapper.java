package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.TenantCustomRoleSummaryRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 自定义角色目录最小投影的受限分页查询映射器。 */
public interface TenantCustomRoleDirectoryMapper {

    List<TenantCustomRoleSummaryRow> selectPage(
            @Param("tenantId") long tenantId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize);

    long count(@Param("tenantId") long tenantId);
}
