package com.aiplatform.foundation.modelruntime.domain.configuration.repository;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationOwnerId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 受控目录与模型配置的聚合持久化端口。
 */
public interface ModelConfigurationRepository {

    Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id);

    /**
     * 返回可由配置者选择的已发布目录条目，草稿和其他非发布状态不得暴露。
     */
    default ControlledModelCatalogPage findPublishedCatalogPage(
            long pageNumber, long pageSize) {
        throw new UnsupportedOperationException("当前模型配置仓储不支持已发布目录分页读取");
    }

    void saveCatalogEntry(ControlledModelCatalogEntry entry);

    void updateCatalogEntry(ControlledModelCatalogEntry entry);

    Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id);

    /**
     * 返回已按范围与活动平台授权过滤的当前成员可见配置；凭据状态由应用层投影。
     */
    default ModelConfigurationPage findVisiblePage(
            TenantId tenantId,
            ModelConfigurationOwnerId membershipId,
            long pageNumber,
            long pageSize) {
        throw new UnsupportedOperationException("当前模型配置仓储不支持可见配置分页");
    }

    void updateConfiguration(ModelConfiguration configuration);

    void save(ModelConfiguration configuration);
}
