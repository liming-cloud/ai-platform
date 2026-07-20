package com.aiplatform.audit.infrastructure.persistence.mapper;

import com.aiplatform.audit.infrastructure.persistence.entity.RagAuthorizationDecisionLogPersistenceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagAuthorizationDecisionLogMapper {
    int insertLog(RagAuthorizationDecisionLogPersistenceEntity entity);
}
