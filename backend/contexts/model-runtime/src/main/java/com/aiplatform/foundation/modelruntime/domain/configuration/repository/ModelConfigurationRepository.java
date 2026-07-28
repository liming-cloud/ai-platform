package com.aiplatform.foundation.modelruntime.domain.configuration.repository;

import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntry;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ControlledModelCatalogEntryId;
import com.aiplatform.foundation.modelruntime.domain.configuration.model.ModelConfiguration;
import java.util.Optional;

/**
 * 受控目录与模型配置的聚合持久化端口。
 */
public interface ModelConfigurationRepository {

    Optional<ControlledModelCatalogEntry> findCatalogEntry(ControlledModelCatalogEntryId id);

    void save(ModelConfiguration configuration);
}
