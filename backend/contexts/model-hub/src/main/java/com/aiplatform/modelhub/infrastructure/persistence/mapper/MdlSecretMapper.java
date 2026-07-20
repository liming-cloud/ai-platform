package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MdlSecretMapper extends BaseMapper<MdlSecretPersistenceEntity> {
    List<MdlSecretPersistenceEntity> findRotationCandidates(
            @Param("activeKeyId") String activeKeyId,
            @Param("targetAlgorithm") String targetAlgorithm,
            @Param("targetVersion") int targetVersion,
            @Param("afterId") long afterId,
            @Param("limit") int limit
    );

    long countRotationBacklog(
            @Param("activeKeyId") String activeKeyId,
            @Param("targetAlgorithm") String targetAlgorithm,
            @Param("targetVersion") int targetVersion
    );

    int rotateIfUnchanged(
            @Param("id") long id,
            @Param("tenantId") long tenantId,
            @Param("expectedVersion") int expectedVersion,
            @Param("oldKeyId") String oldKeyId,
            @Param("oldAlgorithm") String oldAlgorithm,
            @Param("oldCipherVersion") int oldCipherVersion,
            @Param("newCiphertext") String newCiphertext,
            @Param("newKeyId") String newKeyId,
            @Param("newAlgorithm") String newAlgorithm,
            @Param("newCipherVersion") int newCipherVersion,
            @Param("rotatedAt") Instant rotatedAt
    );
}
