package com.aiplatform.foundation.modelruntime.domain.configuration.repository;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfigurationId;
import java.util.Optional;

/**
 * 受控目录与模型配置的聚合持久化端口。
 */
public interface ModelConfigurationRepository {

    Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id);

    void saveCatalogEntry(ControlledModelCatalogEntry entry);

    void updateCatalogEntry(ControlledModelCatalogEntry entry);

    Optional<ModelConfiguration> findConfiguration(ModelConfigurationId id);

    void updateConfiguration(ModelConfiguration configuration);

    void save(ModelConfiguration configuration);
}
