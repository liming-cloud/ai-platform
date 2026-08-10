package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantRoleBindingCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantRoleBindingView;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantBuiltInRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.TenantRoleBindingService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受权编排成员角色绑定，并使权限变更后的租户会话立即失效。
 */
public class TenantRoleBindingApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");

    private final TenantRoleBindingStore store;
    private final TenantMemberSessionRevoker sessions;
    private final Clock clock;

    public TenantRoleBindingApplicationService(
            TenantRoleBindingStore store,
            TenantMemberSessionRevoker sessions,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "角色绑定存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "成员会话撤销器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * @throws IllegalArgumentException 当当前成员无权、目标不存在或绑定已存在时拒绝。
     */
    @Transactional
    public TenantRoleBindingView bind(
            TrustedRequestContext context,
            TenantRoleBindingCommand command) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        command = Objects.requireNonNull(command, "角色绑定命令不能为空");
        if (!context.hasPermission(new com.aiplatform.foundation.security.identity.PermissionCode(ACCESS_MANAGE.value()))) {
            throw new IllegalArgumentException("当前成员没有角色绑定权限");
        }
        final TenantBuiltInRole role;
        try {
            role = TenantBuiltInRole.valueOf(command.roleCode());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("请求角色尚未发布", exception);
        }
        var membershipId = new MemberId(command.membershipId());
        var membership = store.lockEnabled(context.tenantId(), membershipId)
                .orElseThrow(() -> new IllegalArgumentException("目标成员不存在或未启用"));
        if (store.hasBinding(context.tenantId(), membershipId, role)) {
            throw new IllegalArgumentException("目标成员已拥有该角色");
        }
        var result = TenantRoleBindingService.bind(
                membership, context.tenantId(), role, clock.instant());
        store.commit(result);
        sessions.revokeTenantSessions(membershipId);
        return TenantRoleBindingView.from(result);
    }
}
