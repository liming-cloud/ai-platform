package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity.AccessPolicyEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 只映射组织拥有的策略事实，普通单表查询使用受租户约束的 Wrapper。 */
public interface AccessPolicyMapper extends BaseMapper<AccessPolicyEntity> {
    /** 当前可授权普通成员在分页前校验身份、固定角色与资格事实。 */
    com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> memberChoices(
            com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> page,
            @org.apache.ibatis.annotations.Param("tenantId") long tenantId, @org.apache.ibatis.annotations.Param("q") String q);
    /** 目录按最新版本联表筛选，分页和总数必须采用相同范围。 */
    com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> findPage(
            com.baomidou.mybatisplus.core.metadata.IPage<java.util.Map<String, Object>> page,
            @org.apache.ibatis.annotations.Param("tenantId") long tenantId,
            @org.apache.ibatis.annotations.Param("q") String q,
            @org.apache.ibatis.annotations.Param("status") String status);
}
