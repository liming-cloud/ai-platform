package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.service.TenantAdministratorGovernance;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity.SubjectIdentityEntity;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.PlatformTenantAdministratorMapper;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.membership.entity.TenantMembershipEntity;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL 平台租户管理员存储，以共同主体锁和乐观成员修订闭合事务。 */
public final class PostgresPlatformTenantAdministratorStore implements PlatformTenantAdministratorStore {

    private final PlatformTenantAdministratorMapper mapper;

    public PostgresPlatformTenantAdministratorStore(PlatformTenantAdministratorMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "租户管理员映射器不能为空");
    }

    @Override
    public void lockTenantGovernanceScope(TenantId tenantId) {
        if (mapper.lockTenantGovernanceScope(requireTenant(tenantId).value()) == null) {
            throw new IllegalArgumentException("租户管理员治理范围不可用");
        }
    }

    @Override
    public Optional<SubjectIdentity> lockActiveSubject(SubjectId subjectId) {
        return optionalSubject(mapper.lockActiveSubject(requireSubject(subjectId).value()));
    }

    @Override
    public Optional<SubjectIdentity> lockSubjectForMembership(
            TenantId tenantId,
            MemberId membershipId) {
        return optionalSubject(mapper.lockSubjectForMembership(
                requireTenant(tenantId).value(), requireMember(membershipId).value()));
    }

    @Override
    public boolean hasActivePlatformAdministrator(SubjectId subjectId) {
        return mapper.hasActivePlatformAdministrator(requireSubject(subjectId).value());
    }

    @Override
    public Optional<TenantMembership> lockMembershipBySubject(
            TenantId tenantId,
            SubjectId subjectId) {
        return optionalMembership(mapper.lockMembershipBySubject(
                requireTenant(tenantId).value(), requireSubject(subjectId).value()));
    }

    @Override
    public Optional<TenantMembership> lockMembership(
            TenantId tenantId,
            MemberId membershipId) {
        return optionalMembership(mapper.lockMembership(
                requireTenant(tenantId).value(), requireMember(membershipId).value()));
    }

    @Override
    public long countOtherEnabledAdministrators(
            TenantId tenantId,
            MemberId membershipId) {
        return mapper.countOtherEnabledAdministrators(
                requireTenant(tenantId).value(), requireMember(membershipId).value());
    }

    @Override
    public void saveAssignment(TenantAdministratorGovernance.Assignment result) {
        result = Objects.requireNonNull(result, "管理员分配结果不能为空");
        var administrator = result.administrator();
        if (result.newlyEstablished()) {
            requireSingleWrite(mapper.insertMembership(
                    administrator.id().value(),
                    administrator.tenantId().value(),
                    administrator.subjectId().value(),
                    administrator.role().name(),
                    administrator.status().name(),
                    administrator.permissionVersion(),
                    administrator.version(),
                    administrator.createdAt()), "管理员成员资格写入失败");
        } else {
            update(administrator);
        }
        mapper.deleteBuiltInRoleBindings(
                administrator.tenantId().value(), administrator.id().value());
        requireSingleWrite(mapper.insertAdministratorRoleBinding(
                administrator.tenantId().value(),
                administrator.id().value(),
                result.roleBinding().boundAt()), "管理员角色绑定写入失败");
    }

    @Override
    public void saveReplacement(TenantAdministratorGovernance.Replacement result) {
        result = Objects.requireNonNull(result, "管理员替换结果不能为空");
        saveAssignment(result.assignment());
        update(result.previousAdministrator());
    }

    @Override
    public void update(TenantMembership membership) {
        membership = Objects.requireNonNull(membership, "管理员成员资格不能为空");
        requireSingleWrite(mapper.updateMembership(
                membership.id().value(),
                membership.tenantId().value(),
                membership.role().name(),
                membership.status().name(),
                membership.permissionVersion(),
                membership.version(),
                membership.version() - 1L), "管理员成员资格更新冲突");
    }

    private static Optional<SubjectIdentity> optionalSubject(SubjectIdentityEntity row) {
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(SubjectIdentity.reconstitute(
                new SubjectId(row.getSubjectId()),
                row.getLoginName(),
                new EncodedPassword(row.getPasswordHash()),
                SubjectIdentityStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt()));
    }

    private static Optional<TenantMembership> optionalMembership(TenantMembershipEntity row) {
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(TenantMembership.reconstitute(
                new MemberId(row.getMembershipId()),
                new TenantId(row.getTenantId()),
                new SubjectId(row.getSubjectId()),
                TenantMembershipRole.valueOf(row.getMembershipRole()),
                TenantMembershipStatus.valueOf(row.getStatus()),
                row.getPermissionVersion(),
                row.getVersion(),
                row.getCreatedAt()));
    }

    private static TenantId requireTenant(TenantId tenantId) {
        return Objects.requireNonNull(tenantId, "租户标识不能为空");
    }

    private static MemberId requireMember(MemberId membershipId) {
        return Objects.requireNonNull(membershipId, "成员标识不能为空");
    }

    private static SubjectId requireSubject(SubjectId subjectId) {
        return Objects.requireNonNull(subjectId, "主体标识不能为空");
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
