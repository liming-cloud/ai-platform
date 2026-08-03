package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.service;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out.TenantLifecycleBackfillStore;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantInitialAdministratorMapping;
import com.aiplatform.foundation.organizationaccess.domain.tenant.service.TenantLifecycleBackfill;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受控部署窗口内的历史租户生命周期回填用例。
 *
 * <p>该服务没有 HTTP 入口，也不读取配置文件或令牌。它只在单一事务中加载锁定事实、调用纯领域服务并
 * 提交操作结果；任何一个租户失败都会回滚整批。</p>
 */
public class TenantLifecycleBackfillService {

    private final TenantLifecycleBackfillStore store;

    public TenantLifecycleBackfillService(TenantLifecycleBackfillStore store) {
        this.store = Objects.requireNonNull(store, "历史租户回填存储不能为空");
    }

    /**
     * 回填当前所有尚无生命周期根的历史租户。
     *
     * @throws IllegalArgumentException 当映射为空、重复、不完整或包含非历史租户时拒绝。
     */
    @Transactional
    public void backfill(List<TenantInitialAdministratorMapping> mappings, Instant occurredAt) {
        mappings = List.copyOf(Objects.requireNonNull(mappings, "历史初始管理员映射不能为空"));
        var backfilledAt = Objects.requireNonNull(occurredAt, "回填时间不能为空");
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("历史初始管理员映射不能为空");
        }
        var tenantIds = new HashSet<com.aiplatform.foundation.shared.kernel.tenant.TenantId>();
        for (var mapping : mappings) {
            if (!tenantIds.add(Objects.requireNonNull(mapping, "历史初始管理员映射不能为空").tenantId())) {
                throw new IllegalArgumentException("同一历史租户只能有一条初始管理员映射");
            }
        }
        var facts = store.lockHistoricalTenants(mappings);
        var historicalTenantIds = new HashSet<com.aiplatform.foundation.shared.kernel.tenant.TenantId>();
        for (var fact : facts) {
            if (!historicalTenantIds.add(fact.tenantId())) {
                throw new IllegalArgumentException("历史租户读取结果不能重复");
            }
        }
        if (!tenantIds.equals(historicalTenantIds)) {
            throw new IllegalArgumentException("历史初始管理员映射必须完整且只覆盖当前历史租户");
        }
        var results = facts.stream()
                .map(fact -> TenantLifecycleBackfill.backfill(
                        fact.tenantId(),
                        fact.tenantCode(),
                        fact.displayName(),
                        fact.targetStatus(),
                        fact.initialAdministrator(),
                        fact.existingMembership(),
                        fact.tenantCreatedAt(),
                        backfilledAt))
                .toList();
        store.commit(results);
    }
}
