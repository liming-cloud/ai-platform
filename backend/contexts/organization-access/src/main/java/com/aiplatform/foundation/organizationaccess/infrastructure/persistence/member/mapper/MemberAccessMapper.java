package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** 查询进程仅注册这两个无秘密、无锁、无写入的成员映射。 */
public interface MemberAccessMapper {
    /** 读取活动租户中的资格和功能事实，不读取密码字段。 */
    Map<String, Object> find(@Param("tenantId") long tenantId, @Param("membershipId") long membershipId);
    /** 读取组织拥有的当前助手关系。 */
    List<Long> assistants(@Param("tenantId") long tenantId, @Param("membershipId") long membershipId);
}
