package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlProviderPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MdlProviderMapper extends BaseMapper<MdlProviderPersistenceEntity> {
    Long nextProviderId();

    int compareAndSet(
            @Param("updated") MdlProviderPersistenceEntity updated,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedStatus") String expectedStatus
    );

    int compareAndSetApprovalDecision(
            @Param("decision") MdlProviderPersistenceEntity decision,
            @Param("expectedVersion") int expectedVersion,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedApprovalRequestId") String expectedApprovalRequestId,
            @Param("expectedEvidenceDigest") String expectedEvidenceDigest,
            @Param("expectedTargetDigest") String expectedTargetDigest
    );
}
