package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection.HistoricalTenantLifecycleRow;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection.HistoricalTenantMembershipRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 历史租户回填所需的受锁事实和原子写入 MyBatis 映射器。 */
public interface TenantLifecycleBackfillMapper {

    List<HistoricalTenantLifecycleRow> lockHistoricalTenants();

    HistoricalTenantMembershipRow lockInitialAdministrator(
            @Param("tenantId") long tenantId,
            @Param("subjectId") long subjectId);

    int insertLifecycle(
            @Param("tenantId") long tenantId,
            @Param("tenantCode") String tenantCode,
            @Param("displayName") String displayName,
            @Param("initialAdministratorId") long initialAdministratorId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    int confirmInitialAdministrator(
            @Param("membershipId") long membershipId,
            @Param("tenantId") long tenantId,
            @Param("subjectId") long subjectId,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion);

    int insertOutbox(
            @Param("eventId") long eventId,
            @Param("tenantId") long tenantId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("occurredAt") Instant occurredAt);
}
