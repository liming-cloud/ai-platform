package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection.TenantMemberCandidateRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

/** 租户成员候选账户的受限分页查询映射器。 */
public interface TenantMemberCandidateMapper {

    /**
     * 按当前租户和登录名筛选可建立成员资格的活动账户。
     *
     * @param page MyBatis-Plus 服务端分页参数
     * @param tenantId 当前租户标识
     * @param pattern 已转义的登录名模糊匹配条件
     * @return 当前页候选与服务器统计总数
     */
    IPage<TenantMemberCandidateRow> selectEligiblePage(
            IPage<?> page,
            @Param("tenantId") long tenantId,
            @Param("pattern") String pattern);
}
