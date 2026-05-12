package org.fujay.ai.platform.model.infrastructure.persistence.repository;

import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toModel;
import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toModelPO;
import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toProvider;
import static org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter.toProviderPO;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.infrastructure.persistence.converter.ModelPersistenceConverter;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelCatalogMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelProviderMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelCatalogPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelProviderPO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusModelProviderRepository implements ModelProviderRepository {

    private final ModelProviderMapper providerMapper;
    private final ModelCatalogMapper modelMapper;

    public MybatisPlusModelProviderRepository(ModelProviderMapper providerMapper, ModelCatalogMapper modelMapper) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
    }

    @Override
    public void save(ModelProvider provider) {
        if (providerMapper.selectById(provider.id()) == null) {
            providerMapper.insert(toProviderPO(provider));
        } else {
            providerMapper.updateById(toProviderPO(provider));
        }
        Map<Long, ModelCatalogPO> existingModels = modelMapper
                .selectList(Wrappers.<ModelCatalogPO>lambdaQuery()
                        .eq(ModelCatalogPO::getTenantId, provider.tenantId())
                        .eq(ModelCatalogPO::getProviderId, provider.id()))
                .stream()
                .collect(Collectors.toMap(ModelCatalogPO::getId, Function.identity(), (first, second) -> first));
        for (ConfiguredModel model : provider.models()) {
            ModelCatalogPO po = toModelPO(model);
            if (existingModels.containsKey(model.id())) {
                modelMapper.updateById(po);
            } else {
                modelMapper.insert(po);
            }
        }
        disableRemovedModels(existingModels, provider);
    }

    @Override
    public Optional<ModelProvider> findById(Long tenantId, Long providerId) {
        return Optional.ofNullable(providerMapper.selectOne(Wrappers.<ModelProviderPO>lambdaQuery()
                        .eq(ModelProviderPO::getTenantId, tenantId)
                        .eq(ModelProviderPO::getId, providerId)))
                .map(po -> toProvider(po, listModels(tenantId, po.getId())));
    }

    @Override
    public Optional<ModelProvider> findByName(Long tenantId, String providerName) {
        return Optional.ofNullable(providerMapper.selectOne(Wrappers.<ModelProviderPO>lambdaQuery()
                        .eq(ModelProviderPO::getTenantId, tenantId)
                        .eq(ModelProviderPO::getProviderName, providerName)))
                .map(po -> toProvider(po, listModels(tenantId, po.getId())));
    }

    @Override
    public List<ModelProvider> listByTenant(Long tenantId) {
        return providerMapper
                .selectList(Wrappers.<ModelProviderPO>lambdaQuery()
                        .eq(ModelProviderPO::getTenantId, tenantId)
                        .orderByDesc(ModelProviderPO::getUpdatedAt))
                .stream()
                .map(po -> toProvider(po, listModels(tenantId, po.getId())))
                .toList();
    }

    @Override
    public List<ConfiguredModel> listModels(Long tenantId, Long providerId) {
        return modelMapper
                .selectList(Wrappers.<ModelCatalogPO>lambdaQuery()
                        .eq(ModelCatalogPO::getTenantId, tenantId)
                        .eq(ModelCatalogPO::getProviderId, providerId)
                        .orderByAsc(ModelCatalogPO::getModelCode))
                .stream()
                .map(ModelPersistenceConverter::toModel)
                .toList();
    }

    @Override
    public Optional<ConfiguredModel> findModel(Long tenantId, Long modelId) {
        return Optional.ofNullable(modelMapper.selectOne(Wrappers.<ModelCatalogPO>lambdaQuery()
                        .eq(ModelCatalogPO::getTenantId, tenantId)
                        .eq(ModelCatalogPO::getId, modelId)))
                .map(ModelPersistenceConverter::toModel);
    }

    private void disableRemovedModels(Map<Long, ModelCatalogPO> existingModels, ModelProvider provider) {
        Set<Long> activeModelIds = provider.models().stream().map(ConfiguredModel::id).collect(Collectors.toSet());
        existingModels.values().stream()
                .filter(existing -> !activeModelIds.contains(existing.getId()))
                .forEach(existing -> {
                    existing.setStatus("DISABLED");
                    existing.setUpdatedAt(provider.updatedAt());
                    modelMapper.updateById(existing);
                });
    }
}
