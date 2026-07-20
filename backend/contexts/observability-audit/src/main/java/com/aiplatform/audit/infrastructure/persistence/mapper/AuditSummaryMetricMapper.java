package com.aiplatform.audit.infrastructure.persistence.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditSummaryMetricMapper {
    long countRagAuthorizationDenialsSince(@Param("since") Instant since);
}
