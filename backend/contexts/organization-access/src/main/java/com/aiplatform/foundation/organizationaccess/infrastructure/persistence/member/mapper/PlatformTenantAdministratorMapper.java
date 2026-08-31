package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection.PlatformTenantAdministratorCandidateRow;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection.PlatformTenantAdministratorSummaryRow;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 平台租户管理员生命周期所需的共同主体锁、成员事实和原子写入映射器。 */
public interface PlatformTenantAdministratorMapper {

    Long lockTenantGovernanceScope(@Param("tenantId") long tenantId);

    SubjectIdentityEntity lockActiveSubject(@Param("subjectId") long subjectId);

    SubjectIdentityEntity lockSubjectForMembership(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    boolean hasActivePlatformAdministrator(@Param("subjectId") long subjectId);

    TenantMembershipEntity lockMembershipBySubject(
            @Param("tenantId") long tenantId,
            @Param("subjectId") long subjectId);

    TenantMembershipEntity lockMembership(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    long countOtherEnabledAdministrators(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    int insertMembership(
            @Param("membershipId") long membershipId,
            @Param("tenantId") long tenantId,
            @Param("subjectId") long subjectId,
            @Param("role") String role,
            @Param("status") String status,
            @Param("permissionVersion") long permissionVersion,
            @Param("version") long version,
            @Param("createdAt") Instant createdAt);

    int updateMembership(
            @Param("membershipId") long membershipId,
            @Param("tenantId") long tenantId,
            @Param("role") String role,
            @Param("status") String status,
            @Param("permissionVersion") long permissionVersion,
            @Param("version") long version,
            @Param("expectedVersion") long expectedVersion);

    int deleteBuiltInRoleBindings(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId);

    int insertAdministratorRoleBinding(
            @Param("tenantId") long tenantId,
            @Param("membershipId") long membershipId,
            @Param("boundAt") Instant boundAt);

    List<PlatformTenantAdministratorSummaryRow> selectAdministratorPage(
            @Param("tenantId") long tenantId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize);

    long countAdministrators(@Param("tenantId") long tenantId);

    List<PlatformTenantAdministratorCandidateRow> selectCandidatePage(
            @Param("tenantId") long tenantId,
            @Param("pattern") String pattern,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize);

    long countCandidates(
            @Param("tenantId") long tenantId,
            @Param("pattern") String pattern);
}
