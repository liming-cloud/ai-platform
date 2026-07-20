package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlConfigPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MdlConfigMapper extends BaseMapper<MdlConfigPersistenceEntity> {
    Long nextModelConfigId();

    int compareAndSet(
            @Param("updated") MdlConfigPersistenceEntity updated,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedStatus") String expectedStatus
    );

    int compareAndSetApprovalDecision(
            @Param("decision") MdlConfigPersistenceEntity decision,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedApprovalRequestId") String expectedApprovalRequestId,
            @Param("expectedEvidenceDigest") String expectedEvidenceDigest,
            @Param("expectedTargetDigest") String expectedTargetDigest
    );
}
