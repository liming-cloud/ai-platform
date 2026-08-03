package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleTransitionStore;
import com.aiplatform.foundation.organizationaccess.domain.member.model.SubjectId;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycle;
import com.aiplatform.foundation.organizationaccess.domain.tenant.model.TenantLifecycleStatus;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleTransitionResult;
import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleTransitionMapper;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL 的租户生命周期受锁读取与原子迁移结果提交。
 *
 * <p>本实现只映射领域结果、执行版本比较与写入最小状态事实；不解释迁移动作或补写遗留租户状态。</p>
 */
public final class PostgresTenantLifecycleTransitionStore implements TenantLifecycleTransitionStore {

    private final TenantLifecycleTransitionMapper mapper;
    private final EntityIdGenerator identifiers;

    public PostgresTenantLifecycleTransitionStore(
            TenantLifecycleTransitionMapper mapper,
            EntityIdGenerator identifiers) {
        this.mapper = Objects.requireNonNull(mapper, "生命周期迁移映射器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "实体标识生成器不能为空");
    }

    @Override
    public Optional<TenantLifecycle> lock(TenantId tenantId) {
        var row = mapper.lock(Objects.requireNonNull(tenantId, "租户标识不能为空").value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(TenantLifecycle.reconstitute(
                new TenantId(row.getTenantId()),
                row.getTenantCode(),
                row.getDisplayName(),
                new SubjectId(row.getInitialAdministratorSubjectId()),
                TenantLifecycleStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCreatedAt()));
    }

    @Override
    public void commit(TenantLifecycleTransitionResult result) {
        result = Objects.requireNonNull(result, "生命周期迁移结果不能为空");
        var lifecycle = result.lifecycle();
        requireSingleWrite(mapper.updateLifecycle(
                lifecycle.id().value(),
                lifecycle.version() - 1L,
                lifecycle.status().name(),
                lifecycle.version()), "租户生命周期迁移并发冲突");
        var change = result.change();
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
