package com.aiplatform.foundation.organizationaccess.application.authorization.service;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantDirectPermissionMigrationManifest;
import com.aiplatform.foundation.organizationaccess.application.authorization.port.out.TenantMemberSessionRevoker;
import com.aiplatform.foundation.organizationaccess.application.directpermissionmigration.port.out.TenantDirectPermissionMigrationStore;
import com.aiplatform.foundation.organizationaccess.domain.authorization.service.TenantDirectPermissionMigration;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编排一份已经人工批准的单成员历史直接权限迁移。
 *
 * <p>该服务无 HTTP 入口，也不接受令牌、当前主体或角色名称推断。清单快照、受锁事实、领域等价校验、数据库
 * 写入和目标成员租户会话撤销必须处于同一事务语义中。</p>
 */
public class TenantDirectPermissionMigrationApplicationService {

    private final TenantDirectPermissionMigrationStore store;
    private final TenantMemberSessionRevoker sessions;

    public TenantDirectPermissionMigrationApplicationService(
            TenantDirectPermissionMigrationStore store,
            TenantMemberSessionRevoker sessions) {
        this.store = Objects.requireNonNull(store, "直接权限迁移存储不能为空");
        this.sessions = Objects.requireNonNull(sessions, "成员会话撤销器不能为空");
    }

    /**
     * @throws IllegalArgumentException 当成员不存在、清单快照过期或清单与受锁成员不一致时拒绝。
     */
    @Transactional
    public void migrate(TenantDirectPermissionMigrationManifest manifest) {
        manifest = Objects.requireNonNull(manifest, "迁移清单不能为空");
        var facts = store.lock(manifest).orElseThrow(() -> new IllegalArgumentException("迁移成员不存在或未启用"));
        var membership = facts.membership();
        if (!manifest.tenantId().equals(membership.tenantId())
                || !manifest.membershipId().equals(membership.id())) {
            throw new IllegalArgumentException("迁移清单与受锁成员资格不一致");
        }
        if (manifest.expectedPermissionVersion() != membership.permissionVersion()) {
            throw new IllegalArgumentException("迁移清单的成员权限修订已过期");
        }
        if (!manifest.expectedDirectPermissions().equals(facts.directPermissions())) {
            throw new IllegalArgumentException("迁移清单的历史直接权限摘要已过期");
        }
        var result = TenantDirectPermissionMigration.migrate(
                membership,
                facts.directPermissions(),
                facts.existingRolePermissions(),
                facts.resultingRolePermissions(),
                manifest.approvedRevocations());
        store.commit(manifest, facts, result);
        sessions.revokeTenantSessions(membership.id());
    }
}
