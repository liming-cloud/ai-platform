package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleBindingRevocationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleBindingRevocationView;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingRevocationStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.TenantCustomRoleBindingService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 受权解除自定义角色绑定，并使目标成员旧租户会话立即失效。 */
public class TenantCustomRoleBindingRevocationApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");

    private final TenantCustomRoleBindingRevocationStore store;
    private final TenantMemberSessionRevoker sessions;
    private final Clock clock;

    /** 构造自定义角色解绑用例。 */
    public TenantCustomRoleBindingRevocationApplicationService(
            TenantCustomRoleBindingRevocationStore store,
            TenantMemberSessionRevoker sessions,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "自定义角色解绑存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "成员会话撤销器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 解除当前租户成员的一项活动自定义角色绑定。
     *
     * @throws IllegalArgumentException 当当前成员无权、目标不可用或绑定不存在时拒绝。
     * @throws IllegalStateException 当解绑会移除租户最后一个访问管理员时拒绝。
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TenantCustomRoleBindingRevocationView unbind(
            TrustedRequestContext context,
            TenantCustomRoleBindingRevocationCommand command) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        command = Objects.requireNonNull(command, "自定义角色解绑命令不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有角色解绑权限");
        }
        var membershipId = new MemberId(command.membershipId());
        var roleId = new TenantCustomRoleId(command.customRoleId());
        var membership = store.lockEnabled(context.tenantId(), membershipId)
                .orElseThrow(() -> new IllegalArgumentException("目标成员不存在或未启用"));
        var role = store.lockActive(context.tenantId(), roleId)
                .orElseThrow(() -> new IllegalArgumentException("自定义角色不存在或未启用"));
        if (!store.hasBinding(context.tenantId(), membershipId, roleId)) {
            throw new IllegalArgumentException("目标成员未拥有该自定义角色");
        }
        var result = TenantCustomRoleBindingService.unbind(
                membership,
                role,
                store.retainsAccessManageAfterRevocation(context.tenantId(), membershipId, roleId),
                store.countOtherEnabledAccessManagers(context.tenantId(), membershipId),
                clock.instant());
        store.commit(result);
        sessions.revokeTenantSessions(membershipId);
        return TenantCustomRoleBindingRevocationView.from(result);
    }
}
