package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.entity.MemberAssistantAuthorizationEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 仅映射组织拥有的授权关系；单表删除由明确租户条件的 Wrapper 完成。 */
public interface MemberAssistantAuthorizationMapper extends BaseMapper<MemberAssistantAuthorizationEntity> { }
