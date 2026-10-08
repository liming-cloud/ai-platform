package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection.TenantMemberSummaryRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

/** 成员目录最小投影的受限分页查询映射器。 */
public interface TenantMemberDirectoryMapper {

    /** 按当前租户、登录名和成员标识稳定读取一页成员目录。 */
    IPage<TenantMemberSummaryRow> selectPage(
            IPage<TenantMemberSummaryRow> page,
            @Param("tenantId") long tenantId,
            @Param("q") String q,
            @Param("status") String status);
}
