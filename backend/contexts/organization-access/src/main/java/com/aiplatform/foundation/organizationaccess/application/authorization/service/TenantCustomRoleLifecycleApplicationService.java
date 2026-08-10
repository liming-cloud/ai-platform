package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleDisableCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRolePermissionChangeCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleView;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleLifecycleStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.TenantCustomRoleLifecycleService;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 受权编排自定义角色变更、受影响成员权限修订和租户会话撤销。 */
public class TenantCustomRoleLifecycleApplicationService {

    private static final com.aiplatform.foundation.security.identity.PermissionCode ACCESS_MANAGE =
            new com.aiplatform.foundation.security.identity.PermissionCode("access:manage");

    private final TenantCustomRoleLifecycleStore store;
    private final TenantMemberSessionRevoker sessions;
    private final Clock clock;

    /** 构造自定义角色生命周期用例。 */
    public TenantCustomRoleLifecycleApplicationService(
            TenantCustomRoleLifecycleStore store,
            TenantMemberSessionRevoker sessions,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "自定义角色生命周期存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "成员会话撤销器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /** 修改活动自定义角色权限，并撤销所有受影响成员的旧租户会话。 */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TenantCustomRoleView changePermissions(
            TrustedRequestContext context,
            TenantCustomRolePermissionChangeCommand command) {
        context = requireManager(context);
        command = Objects.requireNonNull(command, "自定义角色权限变更命令不能为空");
        var roleId = new TenantCustomRoleId(command.customRoleId());
        var role = lockActive(context, roleId);
        var permissions = command.permissionCodes().stream().map(PermissionCode::new)
                .collect(Collectors.toUnmodifiableSet());
        if (role.permissions().equals(permissions)) {
            return TenantCustomRoleView.from(role);
        }
        var result = TenantCustomRoleLifecycleService.changePermissions(
                role,
                permissions,
                store.lockEnabledBoundMembers(context.tenantId(), roleId),
                store.countEnabledAccessManagersAfterChange(
                        context.tenantId(), roleId, permissions.contains(new PermissionCode("access:manage"))),
                clock.instant());
        store.commit(result);
        revokeMembers(result);
        return TenantCustomRoleView.from(result.role());
    }

    /** 停用活动自定义角色，并撤销所有受影响成员的旧租户会话。 */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TenantCustomRoleView disable(
            TrustedRequestContext context,
            TenantCustomRoleDisableCommand command) {
        context = requireManager(context);
        command = Objects.requireNonNull(command, "自定义角色停用命令不能为空");
        var roleId = new TenantCustomRoleId(command.customRoleId());
        var role = lockActive(context, roleId);
        var result = TenantCustomRoleLifecycleService.disable(
                role,
                store.lockEnabledBoundMembers(context.tenantId(), roleId),
                store.countEnabledAccessManagersAfterChange(context.tenantId(), roleId, false),
                clock.instant());
        store.commit(result);
        revokeMembers(result);
        return TenantCustomRoleView.from(result.role());
    }

    private TrustedRequestContext requireManager(TrustedRequestContext context) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有角色管理权限");
        }
        return context;
    }

    private com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole lockActive(
            TrustedRequestContext context,
            TenantCustomRoleId roleId) {
        return store.lockActive(context.tenantId(), roleId)
                .orElseThrow(() -> new IllegalArgumentException("自定义角色不存在或未启用"));
    }

    private void revokeMembers(
            com.aiplatform.foundation.organizationaccess.domain.authorization.operation.TenantCustomRoleLifecycleResult result) {
        for (var membership : result.revisedMemberships()) {
            sessions.revokeTenantSessions(membership.id());
        }
    }
}
