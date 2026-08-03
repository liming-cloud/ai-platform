package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection.TenantLifecycleRow;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 租户生命周期迁移所需的受锁读取与最小原子写入 MyBatis 映射器。
 */
public interface TenantLifecycleTransitionMapper {

    TenantLifecycleRow lock(@Param("tenantId") long tenantId);

    int updateLifecycle(
            @Param("tenantId") long tenantId,
            @Param("expectedVersion") long expectedVersion,
            @Param("status") String status,
            @Param("nextVersion") long nextVersion);

    int insertOutbox(
            @Param("eventId") long eventId,
            @Param("tenantId") long tenantId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("occurredAt") Instant occurredAt);
}
