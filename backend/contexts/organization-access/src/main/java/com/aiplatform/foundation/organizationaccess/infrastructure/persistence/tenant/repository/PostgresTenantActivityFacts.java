package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.repository;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.mapper.TenantLifecycleReadMapper;
import com.aiplatform.foundation.security.identity.TenantActivityFacts;
import java.util.Objects;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 租户所有者提供当前活动事实；只读检查不装配账户管理或对外暴露生命周期模型。 */
public final class PostgresTenantActivityFacts implements TenantActivityFacts {
    private final TenantLifecycleReadMapper mapper;

    public PostgresTenantActivityFacts(TenantLifecycleReadMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    /** 同一提交事务按租户 ID 排序共享锁，失效事实使整个供应声明回滚。 */
    @Override
    public void requireActive(Set<Long> tenantIds) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("活动租户锁必须在短写事务内执行");
        for (var id : Objects.requireNonNull(tenantIds).stream().sorted().toList())
            if (mapper.lockActive(id) == null) throw new IllegalArgumentException("拟授权租户不存在或不是活动状态");
    }

    /** 每次读取当前活动状态，不沿用创建时选择或历史缓存。 */
    @Override
    public boolean isActive(long tenantId) { return tenantId > 0 && mapper.isActive(tenantId); }
}
