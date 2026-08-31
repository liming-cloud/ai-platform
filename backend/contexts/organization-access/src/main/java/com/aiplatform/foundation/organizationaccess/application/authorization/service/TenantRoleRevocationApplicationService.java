package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantRoleRevocationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantRoleRevocationView;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleRevocationStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.TenantRoleBindingService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.service.TenantMemberGovernance;
import com.aiplatform.foundation.organizationaccess.domain.shared.exception.OrganizationAccessRuleViolation;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权撤销内置角色，并使成员旧租户会话立即失效。 */
public class TenantRoleRevocationApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");

    private final TenantRoleRevocationStore store;
    private final TenantMemberSessionRevoker sessions;
    private final Clock clock;

    /** 构造内置角色撤销用例。 */
    public TenantRoleRevocationApplicationService(
            TenantRoleRevocationStore store,
            TenantMemberSessionRevoker sessions,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "角色撤销存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "成员会话撤销器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 撤销当前租户成员的一项内置角色绑定。
     *
     * @throws IllegalArgumentException 当当前成员无权、角色未发布、目标不可用或绑定不存在时拒绝。
     * @throws IllegalStateException 当撤销会移除租户最后一个访问管理员时拒绝。
     * @throws OrganizationAccessRuleViolation 当命令试图治理租户管理员时拒绝。
     */
    @Transactional
    public TenantRoleRevocationView revoke(
            TrustedRequestContext context,
            TenantRoleRevocationCommand command) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        command = Objects.requireNonNull(command, "角色撤销命令不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有角色撤销权限");
        }
        final TenantBuiltInRole role;
        try {
            role = TenantBuiltInRole.valueOf(command.roleCode());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("请求角色尚未发布", exception);
        }
        TenantMemberGovernance.requireOrdinaryRole(role);
        var membershipId = new MemberId(command.membershipId());
        var membership = store.lockEnabled(context.tenantId(), membershipId)
                .orElseThrow(() -> new IllegalArgumentException("目标成员不存在或未启用"));
        TenantMemberGovernance.requireOrdinaryTarget(membership);
        if (!store.hasBinding(context.tenantId(), membershipId, role)) {
            throw new IllegalArgumentException("目标成员未拥有该角色");
        }
        var result = TenantRoleBindingService.revoke(
                membership,
                context.tenantId(),
                role,
                store.retainsAccessManageAfterRevocation(context.tenantId(), membershipId, role),
                store.countOtherEnabledAccessManagers(context.tenantId(), membershipId),
                clock.instant());
        store.commit(result);
        sessions.revokeTenantSessions(membershipId);
        return TenantRoleRevocationView.from(result);
    }
}
