package org.fujay.ai.platform.model.domain.repository;

import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;

public interface ModelProviderRepository {

    void save(ModelProvider provider);

    Optional<ModelProvider> findById(Long tenantId, Long providerId);

    Optional<ModelProvider> findByName(Long tenantId, String providerName);

    List<ModelProvider> listByTenant(Long tenantId);

    List<ConfiguredModel> listModels(Long tenantId, Long providerId);

    Optional<ConfiguredModel> findModel(Long tenantId, Long modelId);
}
