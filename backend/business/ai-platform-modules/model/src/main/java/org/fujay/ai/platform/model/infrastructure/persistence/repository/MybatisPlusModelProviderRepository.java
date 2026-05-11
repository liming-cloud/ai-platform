package org.fujay.ai.platform.model.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelCatalogMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelInvocationRecordMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.ModelProviderMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.PlatformModelOptionMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.mapper.PlatformProviderTypeMapper;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelCatalogPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelInvocationRecordPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.ModelProviderPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformModelOptionPO;
import org.fujay.ai.platform.model.infrastructure.persistence.po.PlatformProviderTypePO;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPlusModelProviderRepository implements ModelProviderRepository {

    private final ModelProviderMapper providerMapper;
    private final ModelCatalogMapper modelMapper;
    private final ModelInvocationRecordMapper invocationMapper;
    private final PlatformProviderTypeMapper providerTypeMapper;
    private final PlatformModelOptionMapper modelOptionMapper;

    public MybatisPlusModelProviderRepository(
            ModelProviderMapper providerMapper,
            ModelCatalogMapper modelMapper,
            ModelInvocationRecordMapper invocationMapper,
            PlatformProviderTypeMapper providerTypeMapper,
            PlatformModelOptionMapper modelOptionMapper) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.invocationMapper = invocationMapper;
        this.providerTypeMapper = providerTypeMapper;
        this.modelOptionMapper = modelOptionMapper;
    }

    @Override
    public void saveProvider(ProviderRecord provider, List<ModelRecord> models) {
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
        for (ModelRecord model : models) {
            ModelCatalogPO po = toModelPO(model);
            if (existingModels.containsKey(model.id())) {
                modelMapper.updateById(po);
            } else {
                modelMapper.insert(po);
            }
        }
        disableRemovedModels(existingModels, models, provider.updatedAt());
    }

    @Override
    public Optional<ProviderRecord> findProvider(Long tenantId, Long providerId) {
        return Optional.ofNullable(providerMapper.selectOne(Wrappers.<ModelProviderPO>lambdaQuery()
                        .eq(ModelProviderPO::getTenantId, tenantId)
                        .eq(ModelProviderPO::getId, providerId)))
                .map(this::toProviderRecord);
    }

    @Override
    public Optional<ProviderRecord> findProviderByName(Long tenantId, String providerName) {
        return Optional.ofNullable(providerMapper.selectOne(Wrappers.<ModelProviderPO>lambdaQuery()
                        .eq(ModelProviderPO::getTenantId, tenantId)
                        .eq(ModelProviderPO::getProviderName, providerName)))
                .map(this::toProviderRecord);
    }

    @Override
    public List<ProviderRecord> listProviders(Long tenantId) {
        return providerMapper
                .selectList(Wrappers.<ModelProviderPO>lambdaQuery()
                        .eq(ModelProviderPO::getTenantId, tenantId)
                        .orderByDesc(ModelProviderPO::getUpdatedAt))
                .stream()
                .map(this::toProviderRecord)
                .toList();
    }

    @Override
    public List<ProviderTypeOptionRecord> listProviderTypeOptions() {
        return providerTypeMapper
                .selectList(Wrappers.<PlatformProviderTypePO>lambdaQuery()
                        .eq(PlatformProviderTypePO::getStatus, "ENABLED")
                        .orderByAsc(PlatformProviderTypePO::getSortOrder))
                .stream()
                .map(this::toProviderTypeOptionRecord)
                .toList();
    }

    @Override
    public List<ModelOptionRecord> listModelOptions() {
        return modelOptionMapper
                .selectList(Wrappers.<PlatformModelOptionPO>lambdaQuery()
                        .eq(PlatformModelOptionPO::getStatus, "ENABLED")
                        .orderByAsc(PlatformModelOptionPO::getSortOrder))
                .stream()
                .map(this::toModelOptionRecord)
                .toList();
    }

    @Override
    public List<ProviderTypeOptionRecord> listAllProviderTypeOptions() {
        return providerTypeMapper
                .selectList(Wrappers.<PlatformProviderTypePO>lambdaQuery()
                        .orderByAsc(PlatformProviderTypePO::getSortOrder))
                .stream()
                .map(this::toProviderTypeOptionRecord)
                .toList();
    }

    @Override
    public List<ModelOptionRecord> listAllModelOptions() {
        return modelOptionMapper
                .selectList(Wrappers.<PlatformModelOptionPO>lambdaQuery()
                        .orderByAsc(PlatformModelOptionPO::getSortOrder))
                .stream()
                .map(this::toModelOptionRecord)
                .toList();
    }

    @Override
    public void saveProviderTypeOption(ProviderTypeOptionRecord option) {
        LocalDateTime now = LocalDateTime.now();
        PlatformProviderTypePO existing = providerTypeMapper.selectById(option.id());
        if (existing == null) {
            existing = providerTypeMapper.selectOne(Wrappers.<PlatformProviderTypePO>lambdaQuery()
                    .eq(PlatformProviderTypePO::getProviderType, option.providerType().name()));
        }
        PlatformProviderTypePO po = toProviderTypePO(option);
        po.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        po.setUpdatedAt(now);
        if (existing == null) {
            providerTypeMapper.insert(po);
        } else {
            po.setId(existing.getId());
            providerTypeMapper.updateById(po);
        }
    }

    @Override
    public void saveModelOption(ModelOptionRecord option) {
        LocalDateTime now = LocalDateTime.now();
        PlatformModelOptionPO existing = modelOptionMapper.selectById(option.id());
        if (existing == null) {
            existing = modelOptionMapper.selectOne(Wrappers.<PlatformModelOptionPO>lambdaQuery()
                    .eq(PlatformModelOptionPO::getProviderTypeId, option.providerTypeId())
                    .eq(PlatformModelOptionPO::getModelCode, option.modelCode())
                    .eq(PlatformModelOptionPO::getCapabilityType, option.capabilityType().name()));
        }
        PlatformModelOptionPO po = toPlatformModelOptionPO(option);
        po.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        po.setUpdatedAt(now);
        if (existing == null) {
            modelOptionMapper.insert(po);
        } else {
            po.setId(existing.getId());
            modelOptionMapper.updateById(po);
        }
    }

    @Override
    public List<ModelRecord> listModels(Long tenantId, Long providerId) {
        return modelMapper
                .selectList(Wrappers.<ModelCatalogPO>lambdaQuery()
                        .eq(ModelCatalogPO::getTenantId, tenantId)
                        .eq(ModelCatalogPO::getProviderId, providerId)
                        .orderByAsc(ModelCatalogPO::getModelCode))
                .stream()
                .map(this::toModelRecord)
                .toList();
    }

    @Override
    public Optional<ModelRecord> findModel(Long tenantId, Long modelId) {
        return Optional.ofNullable(modelMapper.selectOne(Wrappers.<ModelCatalogPO>lambdaQuery()
                        .eq(ModelCatalogPO::getTenantId, tenantId)
                        .eq(ModelCatalogPO::getId, modelId)))
                .map(this::toModelRecord);
    }

    @Override
    public void appendInvocation(InvocationRecord invocation) {
        invocationMapper.insert(toInvocationPO(invocation));
    }

    private ModelProviderPO toProviderPO(ProviderRecord record) {
        ModelProviderPO po = new ModelProviderPO();
        po.setId(record.id());
        po.setTenantId(record.tenantId());
        po.setProviderName(record.providerName());
        po.setProviderType(record.providerType().name());
        po.setBaseUrl(record.baseUrl());
        po.setApiKeyCiphertext(record.apiKeyCiphertext());
        po.setApiKeyLast4(record.apiKeyLast4());
        po.setStatus(record.status());
        po.setBudgetUsd(record.budgetUsd());
        po.setCreatedAt(record.createdAt());
        po.setUpdatedAt(record.updatedAt());
        return po;
    }

    private PlatformProviderTypePO toProviderTypePO(ProviderTypeOptionRecord record) {
        PlatformProviderTypePO po = new PlatformProviderTypePO();
        po.setId(record.id());
        po.setProviderType(record.providerType().name());
        po.setDisplayName(record.displayName());
        po.setDefaultBaseUrl(record.defaultBaseUrl());
        po.setStatus(record.status());
        po.setSortOrder(record.sortOrder());
        return po;
    }

    private void disableRemovedModels(Map<Long, ModelCatalogPO> existingModels, List<ModelRecord> models, LocalDateTime updatedAt) {
        Set<Long> activeModelIds = models.stream().map(ModelRecord::id).collect(Collectors.toSet());
        existingModels.values().stream()
                .filter(existing -> !activeModelIds.contains(existing.getId()))
                .forEach(existing -> {
                    existing.setStatus("DISABLED");
                    existing.setUpdatedAt(updatedAt);
                    modelMapper.updateById(existing);
                });
    }

    private ModelCatalogPO toModelPO(ModelRecord record) {
        ModelParameterDefaults defaults = record.defaults();
        ModelCatalogPO po = new ModelCatalogPO();
        po.setId(record.id());
        po.setTenantId(record.tenantId());
        po.setProviderId(record.providerId());
        po.setModelCode(record.modelCode());
        po.setDisplayName(record.displayName());
        po.setCapabilityType(record.capabilityType().name());
        po.setStatus(record.status());
        po.setContextWindow(defaults.contextWindow());
        po.setMaxOutputTokens(defaults.maxOutputTokens());
        po.setEmbeddingDimensions(defaults.embeddingDimensions());
        po.setDefaultTemperature(defaults.temperature());
        po.setDefaultTopP(defaults.topP());
        po.setDefaultTopK(defaults.topK());
        po.setDefaultFrequencyPenalty(defaults.frequencyPenalty());
        po.setDefaultPresencePenalty(defaults.presencePenalty());
        po.setDefaultReasoningEffort(defaults.reasoningEffort());
        po.setCreatedAt(record.createdAt());
        po.setUpdatedAt(record.updatedAt());
        return po;
    }

    private PlatformModelOptionPO toPlatformModelOptionPO(ModelOptionRecord record) {
        ModelParameterDefaults defaults = record.defaults();
        PlatformModelOptionPO po = new PlatformModelOptionPO();
        po.setId(record.id());
        po.setProviderTypeId(record.providerTypeId());
        po.setModelCode(record.modelCode());
        po.setDisplayName(record.displayName());
        po.setCapabilityType(record.capabilityType().name());
        po.setContextWindow(defaults.contextWindow());
        po.setMaxOutputTokens(defaults.maxOutputTokens());
        po.setEmbeddingDimensions(defaults.embeddingDimensions());
        po.setDefaultTemperature(defaults.temperature());
        po.setDefaultTopP(defaults.topP());
        po.setDefaultTopK(defaults.topK());
        po.setDefaultFrequencyPenalty(defaults.frequencyPenalty());
        po.setDefaultPresencePenalty(defaults.presencePenalty());
        po.setDefaultReasoningEffort(defaults.reasoningEffort());
        po.setStatus(record.status());
        po.setSortOrder(record.sortOrder());
        return po;
    }

    private ModelInvocationRecordPO toInvocationPO(InvocationRecord record) {
        ModelInvocationRecordPO po = new ModelInvocationRecordPO();
        po.setId(record.id());
        po.setTenantId(record.tenantId());
        po.setProviderId(record.providerId());
        po.setModelId(record.modelId());
        po.setProviderType(record.providerType().name());
        po.setModelCode(record.modelCode());
        po.setCapabilityType(record.capabilityType().name());
        po.setStatus(record.status());
        po.setLatencyMs(record.latencyMs());
        po.setPromptTokens(record.promptTokens());
        po.setCompletionTokens(record.completionTokens());
        po.setTotalTokens(record.totalTokens());
        po.setErrorCode(record.errorCode());
        po.setErrorMessage(record.errorMessage());
        po.setCreatedAt(record.createdAt());
        return po;
    }

    private ProviderRecord toProviderRecord(ModelProviderPO po) {
        return new ProviderRecord(
                po.getId(),
                po.getTenantId(),
                po.getProviderName(),
                ProviderType.valueOf(po.getProviderType()),
                po.getBaseUrl(),
                po.getApiKeyCiphertext(),
                po.getApiKeyLast4(),
                po.getStatus(),
                po.getBudgetUsd(),
                po.getCreatedAt(),
                po.getUpdatedAt());
    }

    private ProviderTypeOptionRecord toProviderTypeOptionRecord(PlatformProviderTypePO po) {
        return new ProviderTypeOptionRecord(
                po.getId(),
                ProviderType.valueOf(po.getProviderType()),
                po.getDisplayName(),
                po.getDefaultBaseUrl(),
                po.getStatus(),
                po.getSortOrder());
    }

    private ModelOptionRecord toModelOptionRecord(PlatformModelOptionPO po) {
        return new ModelOptionRecord(
                po.getId(),
                po.getProviderTypeId(),
                po.getModelCode(),
                po.getDisplayName(),
                ModelCapabilityType.valueOf(po.getCapabilityType()),
                new ModelParameterDefaults(
                        po.getContextWindow(),
                        po.getMaxOutputTokens(),
                        po.getEmbeddingDimensions(),
                        po.getDefaultTemperature(),
                        po.getDefaultTopP(),
                        po.getDefaultTopK(),
                        po.getDefaultFrequencyPenalty(),
                        po.getDefaultPresencePenalty(),
                        po.getDefaultReasoningEffort()),
                po.getStatus(),
                po.getSortOrder());
    }

    private ModelRecord toModelRecord(ModelCatalogPO po) {
        return new ModelRecord(
                po.getId(),
                po.getTenantId(),
                po.getProviderId(),
                po.getModelCode(),
                po.getDisplayName(),
                ModelCapabilityType.valueOf(po.getCapabilityType()),
                po.getStatus(),
                new ModelParameterDefaults(
                        po.getContextWindow(),
                        po.getMaxOutputTokens(),
                        po.getEmbeddingDimensions(),
                        po.getDefaultTemperature(),
                        po.getDefaultTopP(),
                        po.getDefaultTopK(),
                        po.getDefaultFrequencyPenalty(),
                        po.getDefaultPresencePenalty(),
                        po.getDefaultReasoningEffort()),
                po.getCreatedAt(),
                po.getUpdatedAt());
    }
}
