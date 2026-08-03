package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.HistoricalTenantLifecycleBackfillFacts;
import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantInitialAdministratorMapping;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleBackfillResult;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleBackfillMapper;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * PostgreSQL 对历史租户的锁定读取和操作结果原子写入。
 *
 * <p>本类不推导初始管理员，不直接设置生命周期状态，也不创建业务结果；这些规则均属于调用它的应用
 * 服务和领域服务。它只把已接受 ADR 固定的历史状态翻译为目标状态，并按结果完成受锁写入。</p>
 */
public final class PostgresTenantLifecycleBackfillStore implements TenantLifecycleBackfillStore {

    private final TenantLifecycleBackfillMapper mapper;
    private final EntityIdGenerator identifiers;

    public PostgresTenantLifecycleBackfillStore(
            TenantLifecycleBackfillMapper mapper,
            EntityIdGenerator identifiers) {
        this.mapper = Objects.requireNonNull(mapper, "租户生命周期回填映射器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
    }

    @Override
    public List<HistoricalTenantLifecycleBackfillFacts> lockHistoricalTenants(
            List<TenantInitialAdministratorMapping> mappings) {
        var mappingsByTenant = mappings.stream().collect(Collectors.toUnmodifiableMap(
                TenantInitialAdministratorMapping::tenantId,
                TenantInitialAdministratorMapping::subjectId));
        return mapper.lockHistoricalTenants().stream()
                .map(row -> toFacts(row, mappingsByTenant.get(new TenantId(row.getTenantId()))))
                .toList();
    }

    @Override
    public void commit(List<TenantLifecycleBackfillResult> results) {
        for (var result : results) {
            var lifecycle = result.lifecycle();
            requireSingleWrite(mapper.insertLifecycle(
                    lifecycle.id().value(),
                    lifecycle.code(),
                    lifecycle.displayName(),
                    lifecycle.initialAdministratorId().value(),
                    lifecycle.status().name(),
                    lifecycle.version(),
                    lifecycle.createdAt()), "租户生命周期写入失败");
            var membership = result.initialAdministrator();
            requireSingleWrite(mapper.confirmInitialAdministrator(
                    membership.id().value(),
                    membership.tenantId().value(),
                    membership.subjectId().value(),
                    membership.version() - 1L,
                    membership.version()), "初始管理员成员资格写入冲突");
            for (var change : result.lifecycleChanges()) {
                requireSingleWrite(mapper.insertOutbox(
                        identifiers.nextId(),
                        change.tenantId().value(),
                        change.status().name(),
                        change.version(),
                        change.occurredAt()), "租户生命周期发件箱写入失败");
            }
        }
    }

    private HistoricalTenantLifecycleBackfillFacts toFacts(
            com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection.HistoricalTenantLifecycleRow row,
            SubjectId mappedSubjectId) {
        var tenantId = new TenantId(row.getTenantId());
        if (mappedSubjectId == null) {
            return new HistoricalTenantLifecycleBackfillFacts(
                    tenantId, row.getTenantCode(), row.getDisplayName(), targetStatus(row.getStatus()), null, null, row.getCreatedAt());
        }
        var membership = mapper.lockInitialAdministrator(tenantId.value(), mappedSubjectId.value());
        if (membership == null) {
            return new HistoricalTenantLifecycleBackfillFacts(
                    tenantId, row.getTenantCode(), row.getDisplayName(), targetStatus(row.getStatus()), null, null, row.getCreatedAt());
        }
        var subject = SubjectIdentity.reconstitute(
                new SubjectId(membership.getSubjectId()),
                membership.getLoginName(),
                new EncodedPassword(membership.getPasswordHash()),
                SubjectIdentityStatus.valueOf(membership.getSubjectStatus()),
                membership.getSubjectVersion(),
                membership.getSubjectCreatedAt());
        var existingMembership = TenantMembership.reconstitute(
                new MemberId(membership.getMembershipId()),
                tenantId,
                subject.id(),
                TenantMembershipRole.valueOf(membership.getMembershipRole()),
                TenantMembershipStatus.valueOf(membership.getMembershipStatus()),
                membership.getPermissionVersion(),
                membership.getMembershipVersion(),
                membership.getMembershipCreatedAt());
        return new HistoricalTenantLifecycleBackfillFacts(
                tenantId,
                row.getTenantCode(),
                row.getDisplayName(),
                targetStatus(row.getStatus()),
                subject,
                existingMembership,
                row.getCreatedAt());
    }

    private static TenantLifecycleStatus targetStatus(String historicalStatus) {
        return switch (historicalStatus) {
            case "ENABLED" -> TenantLifecycleStatus.ACTIVE;
            case "DISABLED" -> TenantLifecycleStatus.SUSPENDED;
            default -> throw new IllegalArgumentException("历史租户状态无法映射为生命周期目标");
        };
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
