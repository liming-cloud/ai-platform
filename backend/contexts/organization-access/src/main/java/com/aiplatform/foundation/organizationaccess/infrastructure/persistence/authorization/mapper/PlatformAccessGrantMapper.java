package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection.PlatformAccessGrantRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 读取平台租户治理授权事实的 MyBatis 映射器。
 */
public interface PlatformAccessGrantMapper {

    List<PlatformAccessGrantRow> findActiveBySubject(@Param("subjectId") long subjectId);
}
