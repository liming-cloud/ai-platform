package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleBindingCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleBindingView;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleBindingStore;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.TenantCustomRoleBindingService;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权编排自定义角色绑定、权限修订和旧租户会话撤销。 */
public class TenantCustomRoleBindingApplicationService {
    private final TenantCustomRoleBindingStore store;
    private final TenantMemberSessionRevoker sessions;
    private final Clock clock;

    /** 构造受权绑定编排器。 */
    public TenantCustomRoleBindingApplicationService(
            TenantCustomRoleBindingStore store,
            TenantMemberSessionRevoker sessions,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "自定义角色绑定存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "成员会话撤销器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 绑定活动自定义角色并撤销目标成员当前的全部租户会话。
     *
     * @throws IllegalArgumentException 当当前成员无权限、目标成员或角色不可用、或绑定已存在时拒绝。
     */
    @Transactional
    public TenantCustomRoleBindingView bind(TrustedRequestContext context, TenantCustomRoleBindingCommand command) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        command = Objects.requireNonNull(command, "自定义角色绑定命令不能为空");
        if (!context.hasPermission(new com.aiplatform.foundation.security.identity.PermissionCode("access:manage"))) {
            throw new IllegalArgumentException("当前成员没有角色绑定权限");
        }
        var memberId = new MemberId(command.membershipId());
        var roleId = new TenantCustomRoleId(command.customRoleId());
        var membership = store.lockEnabled(context.tenantId(), memberId)
                .orElseThrow(() -> new IllegalArgumentException("目标成员不存在或未启用"));
        var role = store.lockActive(context.tenantId(), roleId)
                .orElseThrow(() -> new IllegalArgumentException("自定义角色不存在或未启用"));
        if (store.hasBinding(context.tenantId(), memberId, roleId)) {
            throw new IllegalArgumentException("目标成员已拥有该自定义角色");
        }
        var result = TenantCustomRoleBindingService.bind(membership, role, clock.instant());
        store.commit(result);
        sessions.revokeTenantSessions(memberId);
        return TenantCustomRoleBindingView.from(result);
    }
}
