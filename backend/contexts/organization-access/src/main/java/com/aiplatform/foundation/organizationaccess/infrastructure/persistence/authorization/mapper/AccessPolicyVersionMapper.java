package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.entity.AccessPolicyVersionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 只映射组织拥有的策略事实，普通单表查询使用受租户约束的 Wrapper。 */
public interface AccessPolicyVersionMapper extends BaseMapper<AccessPolicyVersionEntity> {
}
