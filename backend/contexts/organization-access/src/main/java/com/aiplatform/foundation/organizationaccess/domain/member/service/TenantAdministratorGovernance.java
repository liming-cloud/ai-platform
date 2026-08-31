package com.aiplatform.foundation.organizationaccess.domain.member.service;

import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantRoleBinding;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembership;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipRole;
import com.aiplatform.foundation.organizationaccess.domain.member.model.TenantMembershipStatus;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleErrorCode;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 平台独占的租户管理员分配、替换与状态治理规则。 */
public final class TenantAdministratorGovernance {

    private TenantAdministratorGovernance() {
    }

    /**
     * 将活动主体分配为目标租户管理员；既有普通成员会原位提升，不复制成员资格。
     *
     * @throws IllegalArgumentException 当既有成员与目标租户或主体不一致时拒绝。
     * @throws IllegalStateException 当主体未启用或已经是启用管理员时拒绝。
     */
    public static Assignment assign(
            TenantId tenantId,
            SubjectIdentity subject,
            Optional<TenantMembership> existingMembership,
            MemberId newMembershipId,
            Instant occurredAt) {
        tenantId = Objects.requireNonNull(tenantId, "租户标识不能为空");
        subject = Objects.requireNonNull(subject, "管理员主体不能为空");
        existingMembership = Objects.requireNonNull(existingMembership, "既有成员资格不能为空");
        newMembershipId = Objects.requireNonNull(newMembershipId, "新成员标识不能为空");
        occurredAt = Objects.requireNonNull(occurredAt, "操作时间不能为空");
        if (!subject.isActive()) {
            throw new IllegalStateException("租户管理员账户未启用");
        }
        final TenantMembership administrator;
        final boolean newlyEstablished;
        if (existingMembership.isEmpty()) {
            administrator = TenantMembership.establishInitialAdministrator(
                    newMembershipId, tenantId, subject.id(), occurredAt);
            newlyEstablished = true;
        } else {
            var existing = existingMembership.orElseThrow();
            if (!existing.tenantId().equals(tenantId)
                    || !existing.subjectId().equals(subject.id())) {
                throw new IllegalArgumentException("既有成员资格与目标租户或主体不一致");
            }
            if (existing.role() == TenantMembershipRole.TENANT_ADMINISTRATOR
                    && existing.status() == TenantMembershipStatus.ENABLED) {
                throw new IllegalStateException("目标账户已经是启用的租户管理员");
            }
            administrator = existing.assignAdministrator();
            newlyEstablished = false;
        }
        return new Assignment(
                administrator,
                new TenantRoleBinding(
                        tenantId,
                        administrator.id(),
                        TenantBuiltInRole.TENANT_ADMINISTRATOR,
                        occurredAt),
                newlyEstablished);
    }

    /**
     * 停用租户管理员并保护最后一个启用管理员。
     *
     * @throws IllegalArgumentException 当目标不是租户管理员时拒绝。
     * @throws OrganizationAccessRuleViolation 当操作会移除最后一个启用管理员时拒绝。
     */
    public static TenantMembership disable(
            TenantMembership administrator,
            long otherEnabledAdministrators) {
        administrator = requireAdministrator(administrator);
        if (administrator.status() == TenantMembershipStatus.ENABLED
                && otherEnabledAdministrators == 0L) {
            throw new OrganizationAccessRuleViolation(
                    OrganizationAccessRuleErrorCode.LAST_ENABLED_TENANT_ADMINISTRATOR);
        }
        return TenantMembershipLifecycle.disable(
                administrator, true, otherEnabledAdministrators);
    }

    /** 恢复已停用的租户管理员；活动主体资格由同一领域调用再次确认。 */
    public static TenantMembership restore(
            SubjectIdentity subject,
            TenantMembership administrator) {
        subject = Objects.requireNonNull(subject, "管理员主体不能为空");
        administrator = requireAdministrator(administrator);
        if (!subject.id().equals(administrator.subjectId())) {
            throw new IllegalArgumentException("管理员主体与成员资格不一致");
        }
        if (!subject.isActive()) {
            throw new IllegalStateException("租户管理员账户未启用");
        }
        if (administrator.status() != TenantMembershipStatus.DISABLED) {
            throw new IllegalStateException("只有已停用管理员可以恢复");
        }
        return TenantMembershipLifecycle.enable(administrator);
    }

    /**
     * 原子替换管理员；新管理员必须先在同一事务结果中处于启用状态，旧管理员随后停用。
     */
    public static Replacement replace(
            TenantMembership previousAdministrator,
            Assignment assignment) {
        previousAdministrator = requireAdministrator(previousAdministrator);
        assignment = Objects.requireNonNull(assignment, "新管理员分配结果不能为空");
        var replacement = requireAdministrator(assignment.administrator());
        if (previousAdministrator.subjectId().equals(replacement.subjectId())) {
            throw new IllegalArgumentException("替换管理员不能与原管理员相同");
        }
        if (replacement.status() != TenantMembershipStatus.ENABLED) {
            throw new IllegalStateException("替换前新管理员必须已启用");
        }
        return new Replacement(
                assignment,
                TenantMembershipLifecycle.disable(previousAdministrator, true, 1L));
    }

    private static TenantMembership requireAdministrator(TenantMembership membership) {
        membership = Objects.requireNonNull(membership, "租户管理员成员资格不能为空");
        if (membership.role() != TenantMembershipRole.TENANT_ADMINISTRATOR) {
            throw new IllegalArgumentException("目标成员不是租户管理员");
        }
        return membership;
    }

    /** 管理员分配后的成员资格与唯一内置角色绑定事实。 */
    public record Assignment(
            TenantMembership administrator,
            TenantRoleBinding roleBinding,
            boolean newlyEstablished) {

        public Assignment {
            Objects.requireNonNull(administrator, "管理员成员资格不能为空");
            Objects.requireNonNull(roleBinding, "管理员角色绑定不能为空");
        }
    }

    /** 管理员替换的单事务领域结果。 */
    public record Replacement(
            Assignment assignment,
            TenantMembership previousAdministrator) {

        public Replacement {
            Objects.requireNonNull(assignment, "新管理员分配结果不能为空");
            Objects.requireNonNull(previousAdministrator, "旧管理员成员资格不能为空");
        }
    }
}
