package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantProvisioningStore;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentity;
import com.aiplatform.foundation.organizationaccess.domain.identity.model.SubjectIdentityStatus;
import com.aiplatform.foundation.organizationaccess.domain.member.model.EncodedPassword;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantProvisioningResult;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantProvisioningMapper;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL 的新租户建立受锁读取与原子操作结果提交。
 *
 * <p>兼容租户行固定写为 `DISABLED`，只为满足遗留外键与列约束；认证状态始终来自生命周期根。领域
 * 状态、初始管理员角色和事件内容均只从操作结果取得。</p>
 */
public final class PostgresTenantProvisioningStore implements TenantProvisioningStore {

    private final TenantProvisioningMapper mapper;
    private final EntityIdGenerator identifiers;

    public PostgresTenantProvisioningStore(
            TenantProvisioningMapper mapper,
            EntityIdGenerator identifiers) {
        this.mapper = Objects.requireNonNull(mapper, "租户建立映射器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
    }

    @Override
    public Optional<SubjectIdentity> lockActiveInitialAdministrator(SubjectId subjectId) {
        var row = mapper.lockActiveInitialAdministrator(
                Objects.requireNonNull(subjectId, "初始管理员主体不能为空").value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(SubjectIdentity.reconstitute(
                new SubjectId(row.getSubjectId()),
                row.getLoginName(),
                new EncodedPassword(row.getPasswordHash()),
                SubjectIdentityStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt()));
    }

    @Override
    public void commit(TenantProvisioningResult result) {
        result = Objects.requireNonNull(result, "租户建立结果不能为空");
        var lifecycle = result.lifecycle();
        requireSingleWrite(mapper.insertTenant(
                lifecycle.id().value(),
                lifecycle.code(),
                lifecycle.displayName(),
                lifecycle.createdAt()), "租户兼容行写入失败");
        requireSingleWrite(mapper.insertLifecycle(
                lifecycle.id().value(),
                lifecycle.code(),
                lifecycle.displayName(),
                lifecycle.initialAdministratorId().value(),
                lifecycle.status().name(),
                lifecycle.version(),
                lifecycle.createdAt()), "租户生命周期写入失败");
        var administrator = result.initialAdministrator();
        requireSingleWrite(mapper.insertInitialAdministrator(
                administrator.id().value(),
                administrator.tenantId().value(),
                administrator.subjectId().value(),
                administrator.role().name(),
                administrator.status().name(),
                administrator.permissionVersion(),
                administrator.version(),
                administrator.createdAt()), "初始管理员成员资格写入失败");
        var change = result.lifecycleChange();
        requireSingleWrite(mapper.insertOutbox(
                identifiers.nextId(),
                change.tenantId().value(),
                change.status().name(),
                change.version(),
                change.occurredAt()), "租户生命周期发件箱写入失败");
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
