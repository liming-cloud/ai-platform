package com.aiplatform.approval.infrastructure.persistence.mapper;

import com.aiplatform.approval.infrastructure.persistence.entity.ApprRecordPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprRecordMapper extends BaseMapper<ApprRecordPersistenceEntity> {
}
