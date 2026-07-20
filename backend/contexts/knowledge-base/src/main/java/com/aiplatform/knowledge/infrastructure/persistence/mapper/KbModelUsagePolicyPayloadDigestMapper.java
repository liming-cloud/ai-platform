package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 调用数据库唯一 canonical JSON 摘要函数，避免 Java 与 PostgreSQL 序列化规则漂移。
 */
@Mapper
public interface KbModelUsagePolicyPayloadDigestMapper {
    String canonicalDigest(@Param("payloadJson") String payloadJson);
}
