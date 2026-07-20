package com.aiplatform.modelhub.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** PostgreSQL JSONB canonical effective options 摘要 seam。 */
@Mapper
public interface ModelRuntimeAuthorityDigestMapper {
    String canonicalEffectiveOptionsDigest(@Param("effectiveOptionsJson") String effectiveOptionsJson);
}
