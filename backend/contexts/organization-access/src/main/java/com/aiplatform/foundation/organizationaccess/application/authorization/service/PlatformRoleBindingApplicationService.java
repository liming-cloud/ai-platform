package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformRoleBindingView;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PersonSessionFamilyRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.PlatformRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.platform.PlatformRoleBindingId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.PlatformRoleBindingGovernance;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.PersonnelScopeEligibility;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权编排平台固定角色绑定、权限修订与全范围会话撤销。 */
public class PlatformRoleBindingApplicationService {

    private final PlatformRoleBindingStore store;
    private final PersonSessionFamilyRevoker sessions;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    /** 构造平台角色治理应用服务。 */
    public PlatformRoleBindingApplicationService(
            PlatformRoleBindingStore store,
            PersonSessionFamilyRevoker sessions,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "平台角色绑定存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "主体会话族撤销器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 为已启用账户绑定一项固定平台角色。
     *
     * @throws IllegalArgumentException 当当前主体无账户治理权限、角色未发布或目标不存在时拒绝。
     * @throws OrganizationAccessRuleViolation 当目标仍拥有启用租户成员资格时拒绝。
     */
    @Transactional
    public PlatformRoleBindingView bind(
            PlatformRequestContext context, long subjectId, String roleCode) {
        requireAccountManager(context);
        var role = publishedRole(roleCode);
        var targetId = new SubjectId(subjectId);
        var target = store.lock(targetId)
                .orElseThrow(() -> new IllegalArgumentException("目标账户不存在"));
        PersonnelScopeEligibility.requirePlatformScope(
                store.countEnabledTenantMemberships(targetId));
        var binding = PlatformRoleBindingGovernance.bind(
                target,
                store.hasActiveBinding(targetId, role),
                new PlatformRoleBindingId(identifiers.nextId()),
                role,
                clock.instant());
        store.bind(binding);
        sessions.revokeAll(targetId);
        return PlatformRoleBindingView.from(binding);
    }

    /**
     * 撤销目标账户的一项固定平台角色。
     *
     * @throws IllegalArgumentException 当当前主体无账户治理权限、角色未发布或目标绑定不存在时拒绝。
     */
    @Transactional
    public PlatformRoleBindingView revoke(
            PlatformRequestContext context, long subjectId, String roleCode) {
        requireAccountManager(context);
        var role = publishedRole(roleCode);
        var targetId = new SubjectId(subjectId);
        var target = store.lock(targetId)
                .orElseThrow(() -> new IllegalArgumentException("目标账户不存在"));
        var binding = store.lockActiveBinding(targetId, role)
                .orElseThrow(() -> new IllegalArgumentException("目标账户未拥有该平台角色"));
        var revoked = PlatformRoleBindingGovernance.revoke(
                target,
                binding,
                store.countOtherActiveAccountAdministrators(targetId),
                clock.instant());
        store.revoke(revoked);
        sessions.revokeAll(targetId);
        return PlatformRoleBindingView.from(revoked);
    }

    private static void requireAccountManager(PlatformRequestContext context) {
        context = Objects.requireNonNull(context, "可信平台上下文不能为空");
        if (!context.hasPermission(PlatformPermissions.ACCOUNT_MANAGE)) {
            throw new IllegalArgumentException("当前主体没有平台角色治理权限");
        }
    }

    private static PlatformRole publishedRole(String roleCode) {
        try {
            return PlatformRole.from(Objects.requireNonNull(roleCode, "平台角色代码不能为空"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("请求角色尚未发布", exception);
        }
    }
}
