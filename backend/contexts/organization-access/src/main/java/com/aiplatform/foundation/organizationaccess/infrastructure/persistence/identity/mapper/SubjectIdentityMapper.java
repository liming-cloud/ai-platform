package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * 读取可认证全局主体的 MyBatis 映射器。
 */
public interface SubjectIdentityMapper extends BaseMapper<SubjectIdentityEntity> {

    SubjectIdentityEntity findActiveByLogin(@Param("loginName") String loginName);
}
