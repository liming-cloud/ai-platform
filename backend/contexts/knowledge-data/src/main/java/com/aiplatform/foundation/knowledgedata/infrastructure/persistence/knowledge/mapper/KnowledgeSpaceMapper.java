package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.mapper;

import com.aiplatform.foundation.knowledgedata.infrastructure.persistence.knowledge.entity.KnowledgeSpaceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识空间的 MyBatis-Plus 映射器。
 */
public interface KnowledgeSpaceMapper extends BaseMapper<KnowledgeSpaceEntity> {

    String lockTenant(@Param("tenantId") String tenantId);

}
