package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.port.out;

import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantInitialAdministratorMapping;
import com.aiplatform.foundation.organizationaccess.domain.tenant.operation.TenantLifecycleBackfillResult;
import java.util.List;

/**
 * 历史租户回填的锁定读取与原子提交端口。
 *
 * <p>映射只作为本次受控部署调用的瞬时输入；实现必须锁定历史事实，并且仅按领域操作结果保存根、
 * 成员角色和最小状态事实。</p>
 */
public interface TenantLifecycleBackfillStore {

    List<HistoricalTenantLifecycleBackfillFacts> lockHistoricalTenants(
            List<TenantInitialAdministratorMapping> mappings);

    void commit(List<TenantLifecycleBackfillResult> results);
}
