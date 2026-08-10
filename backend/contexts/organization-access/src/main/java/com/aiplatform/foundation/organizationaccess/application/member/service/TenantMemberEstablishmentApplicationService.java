package com.aiplatform.foundation.organizationaccess.application.member.service;

import com.aiplatform.foundation.organizationaccess.application.member.dto.TenantMemberStatusView;
import com.aiplatform.foundation.organizationaccess.application.member.port.out.TenantMemberEstablishmentStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.member.service.TenantMembershipLifecycle;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** 受权将已启用全局主体加入当前租户成员目录。 */
public class TenantMemberEstablishmentApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");
    private final TenantMemberEstablishmentStore store;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    /** 构造成员建立用例。 */
    public TenantMemberEstablishmentApplicationService(
            TenantMemberEstablishmentStore store, EntityIdGenerator identifiers) {
        this(store, identifiers, Clock.systemUTC());
    }

    TenantMemberEstablishmentApplicationService(
            TenantMemberEstablishmentStore store, EntityIdGenerator identifiers, Clock clock) {
        this.store = Objects.requireNonNull(store, "成员建立存储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 建立当前租户的普通成员资格。
     *
     * @throws IllegalArgumentException 当当前成员无权、全局主体不可用或成员资格已存在时拒绝。
     */
    @Transactional
    public TenantMemberStatusView establish(TrustedRequestContext context, long subjectId) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (!context.hasPermission(ACCESS_MANAGE)) {
            throw new IllegalArgumentException("当前成员没有成员治理权限");
        }
        var subject = new SubjectId(subjectId);
        var activeSubject = store.lockActiveSubject(subject)
                .orElseThrow(() -> new IllegalArgumentException("目标全局主体不存在或未启用"));
        if (store.hasMembership(context.tenantId(), activeSubject)) {
            throw new IllegalArgumentException("目标全局主体已是当前租户成员");
        }
        var membership = TenantMembershipLifecycle.establish(
                new com.aiplatform.foundation.organizationaccess.domain.member.model.MemberId(identifiers.nextId()),
                context.tenantId(), activeSubject, clock.instant());
        store.insert(membership);
        return TenantMemberStatusView.from(membership);
    }
}
