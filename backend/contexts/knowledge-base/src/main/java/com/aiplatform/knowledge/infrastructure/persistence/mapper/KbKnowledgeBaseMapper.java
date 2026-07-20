package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import com.aiplatform.knowledge.infrastructure.persistence.entity.KbKnowledgeBasePersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbKnowledgeBaseMapper extends BaseMapper<KbKnowledgeBasePersistenceEntity> {
    Long nextKnowledgeBaseId();
}
