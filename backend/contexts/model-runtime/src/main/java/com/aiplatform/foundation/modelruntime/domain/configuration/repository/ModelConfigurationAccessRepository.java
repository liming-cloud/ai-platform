package com.aiplatform.foundation.modelruntime.domain.configuration.repository;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrant;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationAccessGrantId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 模型配置使用授权的持久化端口，活动状态是运行时授权的唯一事实。
 */
public interface ModelConfigurationAccessRepository {

    Optional<ModelConfigurationAccessGrant> findById(ModelConfigurationAccessGrantId id);

    boolean hasActiveGrant(
            ModelConfigurationId configurationId,
            TenantId tenantId,
            ModelConfigurationOwnerId membershipId);

    void save(ModelConfigurationAccessGrant grant);

    void update(ModelConfigurationAccessGrant grant);
}
