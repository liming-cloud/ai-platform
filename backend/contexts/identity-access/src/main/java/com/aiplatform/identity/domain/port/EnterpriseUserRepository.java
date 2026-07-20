package com.aiplatform.identity.domain.port;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.shared.kernel.TenantId;
import java.util.Optional;

/**
 * 用户仓储端口，基础设施实现必须保证 tenantId 参与查询条件。
 */
public interface EnterpriseUserRepository {
    Optional<EnterpriseUser> findByLoginName(TenantId tenantId, String loginName);

    Optional<EnterpriseUser> findById(TenantId tenantId, String userId);

    EnterpriseUser save(EnterpriseUser user);
}
