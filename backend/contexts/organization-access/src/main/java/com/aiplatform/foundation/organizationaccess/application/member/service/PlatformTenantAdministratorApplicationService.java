package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantAdministratorView;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.PlatformTenantAdministratorStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.PersonnelScopeEligibility;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.service.TenantAdministratorGovernance;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 平台受权编排租户管理员分配、替换、停用、恢复和全租户会话撤销。 */
public class PlatformTenantAdministratorApplicationService {

    private final PlatformTenantAdministratorStore store;
    private final PersonSessionFamilyRevoker sessions;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    /** 构造平台租户管理员生命周期用例。 */
    public PlatformTenantAdministratorApplicationService(
            PlatformTenantAdministratorStore store,
            PersonSessionFamilyRevoker sessions,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "租户管理员存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "主体会话族撤销器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 为目标租户分配管理员；既有普通成员会原位提升。
     *
     * @throws IllegalArgumentException 当平台上下文、租户或候选账户不可用时拒绝。
     */
    @Transactional
    public TenantAdministratorView assign(
            PlatformRequestContext context,
            String tenantId,
            long subjectId) {
        requireTenantGovernor(context);
        var targetTenant = TenantId.parse(tenantId);
        store.lockTenantGovernanceScope(targetTenant);
        var targetSubject = new SubjectId(subjectId);
        var subject = store.lockActiveSubject(targetSubject)
                .orElseThrow(() -> new IllegalArgumentException("租户管理员候选不可用"));
        PersonnelScopeEligibility.requireTenantScope(
                store.hasActivePlatformAdministrator(targetSubject));
        var assignment = TenantAdministratorGovernance.assign(
                targetTenant,
                subject,
                store.lockMembershipBySubject(targetTenant, targetSubject),
                new MemberId(identifiers.nextId()),
                clock.instant());
        store.saveAssignment(assignment);
        sessions.revokeAll(targetSubject);
        return TenantAdministratorView.from(assignment.administrator(), subject.loginName());
    }

    /** 停用一个租户管理员，并保护目标租户最后一个启用管理员。 */
    @Transactional
    public TenantAdministratorView disable(
            PlatformRequestContext context,
            String tenantId,
            long membershipId,
            long expectedVersion) {
        requireTenantGovernor(context);
        var targetTenant = TenantId.parse(tenantId);
        store.lockTenantGovernanceScope(targetTenant);
        var targetMember = new MemberId(membershipId);
        var subject = store.lockSubjectForMembership(targetTenant, targetMember)
                .orElseThrow(() -> new IllegalArgumentException("租户管理员不可用"));
        var administrator = lockedAdministrator(targetTenant, targetMember, expectedVersion);
        var disabled = TenantAdministratorGovernance.disable(
                administrator,
                store.countOtherEnabledAdministrators(targetTenant, targetMember));
        if (disabled != administrator) {
            store.update(disabled);
            sessions.revokeAll(subject.id());
        }
        return TenantAdministratorView.from(disabled, subject.loginName());
    }

    /** 恢复一个已停用租户管理员，并再次检查平台／租户范围互斥。 */
    @Transactional
    public TenantAdministratorView restore(
            PlatformRequestContext context,
            String tenantId,
            long membershipId,
            long expectedVersion) {
        requireTenantGovernor(context);
        var targetTenant = TenantId.parse(tenantId);
        store.lockTenantGovernanceScope(targetTenant);
        var targetMember = new MemberId(membershipId);
        var subject = store.lockSubjectForMembership(targetTenant, targetMember)
                .orElseThrow(() -> new IllegalArgumentException("租户管理员账户未启用"));
        PersonnelScopeEligibility.requireTenantScope(
                store.hasActivePlatformAdministrator(subject.id()));
        var administrator = lockedAdministrator(targetTenant, targetMember, expectedVersion);
        var restored = TenantAdministratorGovernance.restore(subject, administrator);
        store.update(restored);
        sessions.revokeAll(subject.id());
        return TenantAdministratorView.from(restored, subject.loginName());
    }

    /**
     * 在单一事务中分配新管理员并停用旧管理员，禁止出现零管理员中间状态。
     */
    @Transactional
    public TenantAdministratorView replace(
            PlatformRequestContext context,
            String tenantId,
            long previousMembershipId,
            long expectedVersion,
            long replacementSubjectId) {
        requireTenantGovernor(context);
        var targetTenant = TenantId.parse(tenantId);
        store.lockTenantGovernanceScope(targetTenant);
        var replacementId = new SubjectId(replacementSubjectId);
        var replacementSubject = store.lockActiveSubject(replacementId)
                .orElseThrow(() -> new IllegalArgumentException("替换管理员候选不可用"));
        PersonnelScopeEligibility.requireTenantScope(
                store.hasActivePlatformAdministrator(replacementId));
        var assignment = TenantAdministratorGovernance.assign(
                targetTenant,
                replacementSubject,
                store.lockMembershipBySubject(targetTenant, replacementId),
                new MemberId(identifiers.nextId()),
                clock.instant());
        var previousId = new MemberId(previousMembershipId);
        var previousSubject = store.lockSubjectForMembership(targetTenant, previousId)
                .orElseThrow(() -> new IllegalArgumentException("原租户管理员不可用"));
        var previous = lockedAdministrator(targetTenant, previousId, expectedVersion);
        var result = TenantAdministratorGovernance.replace(previous, assignment);
        store.saveReplacement(result);
        sessions.revokeAll(replacementId);
        sessions.revokeAll(previousSubject.id());
        return TenantAdministratorView.from(result.assignment().administrator(), replacementSubject.loginName());
    }

    private TenantMembership lockedAdministrator(
            TenantId tenantId,
            MemberId membershipId,
            long expectedVersion) {
        var membership = store.lockMembership(tenantId, membershipId)
                .orElseThrow(() -> new IllegalArgumentException("租户管理员不可用"));
        if (expectedVersion < 0L || membership.version() != expectedVersion) {
            throw new IllegalArgumentException("租户管理员状态已变化，请刷新后重试");
        }
        return membership;
    }

    private static void requireTenantGovernor(PlatformRequestContext context) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.TENANT_GOVERNANCE_WRITE)) {
            throw new IllegalArgumentException("当前主体没有租户管理员治理权限");
        }
    }
}
