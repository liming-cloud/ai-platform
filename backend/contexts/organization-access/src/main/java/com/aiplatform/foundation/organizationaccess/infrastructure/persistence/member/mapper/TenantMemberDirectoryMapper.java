package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection.TenantMemberSummaryRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 成员目录最小投影的受限分页查询映射器。 */
public interface TenantMemberDirectoryMapper {

    /** 按当前租户、登录名和成员标识稳定读取一页成员目录。 */
    List<TenantMemberSummaryRow> selectPage(
            @Param("tenantId") long tenantId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize);

    /** 统计当前租户成员目录的总数。 */
    long count(@Param("tenantId") long tenantId);
}
