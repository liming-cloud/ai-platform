package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleCreationCommand;
import com.aiplatform.foundation.organizationaccess.application.authorization.dto.TenantCustomRoleView;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantCustomRoleStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.PermissionCode;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRole;
import com.aiplatform.foundation.organizationaccess.domain.authorization.model.TenantCustomRoleId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受权建立租户自定义角色；权限目录与角色状态不变量只由领域聚合根维护。
 */
public class TenantCustomRoleApplicationService {

    private static final PermissionCode ACCESS_MANAGE = new PermissionCode("access:manage");

    private final TenantCustomRoleStore store;
    private final EntityIdGenerator identifiers;
    private final Clock clock;

    public TenantCustomRoleApplicationService(
            TenantCustomRoleStore store,
            EntityIdGenerator identifiers,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "租户自定义角色存储不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    /**
     * 创建当前租户内可用于后续成员绑定的自定义角色。
     *
     * @throws IllegalArgumentException 当当前成员无访问管理权限、名称重复或权限不在发布目录时拒绝。
     */
    @Transactional
    public TenantCustomRoleView create(
            TrustedRequestContext context,
            TenantCustomRoleCreationCommand command) {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        command = Objects.requireNonNull(command, "租户自定义角色创建命令不能为空");
        if (!context.hasPermission(new com.aiplatform.foundation.security.identity.PermissionCode(ACCESS_MANAGE.value()))) {
            throw new IllegalArgumentException("当前成员没有角色管理权限");
        }
        if (store.hasActiveName(context.tenantId(), command.name().trim())) {
            throw new IllegalArgumentException("当前租户已存在同名活动自定义角色");
        }
        var role = TenantCustomRole.create(
                new TenantCustomRoleId(identifiers.nextId()),
                context.tenantId(),
                command.name(),
                command.permissionCodes().stream().map(PermissionCode::new)
                        .collect(Collectors.toUnmodifiableSet()),
                Instant.now(clock));
        store.insert(role);
        return TenantCustomRoleView.from(role);
    }
}
