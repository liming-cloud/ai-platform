package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberStatusView;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberStatusStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId;
import com.aiplatform.foundation.organizationaccess.domain.member.service.TenantMembershipLifecycle;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权编排租户成员停用，并在权限变更后撤销该成员的租户会话。 */
public class TenantMemberStatusApplicationService {

    private static final com.aiplatform.foundation.security.identity.PermissionCode ACCESS_MANAGE =
            new com.aiplatform.foundation.security.identity.PermissionCode("access:manage");

    private final TenantMemberStatusStore store;
    private final TenantMemberSessionRevoker sessions;

    /** 构造成员状态变更用例。 */
    public TenantMemberStatusApplicationService(
            TenantMemberStatusStore store,
            TenantMemberSessionRevoker sessions) {
        this.store = Objects.requireNonNull(store, "成员状态存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "成员会话撤销器不能为空");
    }

    /**
     * 停用当前租户成员，并撤销其已签发的全部租户会话。
     *
     * @throws IllegalArgumentException 当当前成员无权或目标成员不存在时拒绝。
     * @throws IllegalStateException 当目标是最后一个有效访问管理员时拒绝。
     */
    @Transactional
    public TenantMemberStatusView disable(TrustedRequestContext context, long membershipId) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有成员治理权限");
        }
        var target = new MemberId(membershipId);
        var membership = store.lock(context.tenantId(), target)
                .orElseThrow(() -> new IllegalArgumentException("目标成员不存在"));
        var disabled = TenantMembershipLifecycle.disable(
                membership,
                store.hasAccessManage(context.tenantId(), target),
                store.countOtherEnabledAccessManagers(context.tenantId(), target));
        if (disabled == membership) {
            return TenantMemberStatusView.from(disabled);
        }
        store.update(disabled);
        sessions.revokeTenantSessions(target);
        return TenantMemberStatusView.from(disabled);
    }

    /**
     * 启用当前租户成员，并撤销其已签发的全部租户会话。
     *
     * @throws IllegalArgumentException 当当前成员无权或目标成员不存在时拒绝。
     */
    @Transactional
    public TenantMemberStatusView enable(TrustedRequestContext context, long membershipId) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有成员治理权限");
        }
        var target = new MemberId(membershipId);
        var membership = store.lock(context.tenantId(), target)
                .orElseThrow(() -> new IllegalArgumentException("目标成员不存在"));
        var enabled = TenantMembershipLifecycle.enable(membership);
        if (enabled == membership) {
            return TenantMemberStatusView.from(enabled);
        }
        store.update(enabled);
        sessions.revokeTenantSessions(target);
        return TenantMemberStatusView.from(enabled);
    }
}
