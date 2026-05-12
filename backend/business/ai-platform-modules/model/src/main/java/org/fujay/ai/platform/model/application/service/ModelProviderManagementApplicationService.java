package org.fujay.ai.platform.model.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.dto.ModelProviderDto;
import org.fujay.ai.platform.model.application.port.CurrentPrincipalPort;
import org.fujay.ai.platform.model.application.port.DomainIdGeneratorPort;
import org.fujay.ai.platform.model.application.port.ProviderSecretPort;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelProviderManagementApplicationService {

    private static final String STATUS_ENABLED = "ENABLED";

    private final ModelProviderRepository providerRepository;
    private final CurrentPrincipalPort currentPrincipalPort;
    private final ProviderSecretPort providerSecretPort;
    private final DomainIdGeneratorPort idGenerator;

    public ModelProviderManagementApplicationService(
            ModelProviderRepository providerRepository,
            CurrentPrincipalPort currentPrincipalPort,
            ProviderSecretPort providerSecretPort,
            DomainIdGeneratorPort idGenerator) {
        this.providerRepository = providerRepository;
        this.currentPrincipalPort = currentPrincipalPort;
        this.providerSecretPort = providerSecretPort;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public ModelProviderDto saveProvider(SaveModelProviderCommand command) {
        Long tenantId = currentPrincipalPort.currentTenantId();
        return TenantContext.callWith(new TenantId(tenantId), () -> saveProviderForTenant(tenantId, command));
    }

    @Transactional(readOnly = true)
    public List<ModelProviderDto> listProviders() {
        Long tenantId = currentPrincipalPort.currentTenantId();
        return TenantContext.callWith(new TenantId(tenantId), () -> providerRepository.listByTenant(tenantId).stream()
                .map(ModelProviderDtoMapper::toProviderDto)
                .toList());
    }

    @Transactional
    public void bootstrapProviderIfAbsent(Long tenantId, SaveModelProviderCommand command) {
        TenantContext.runWith(new TenantId(tenantId), () -> {
            if (providerRepository.findByName(tenantId, command.providerName()).isEmpty()) {
                saveProviderForTenant(tenantId, command);
            }
        });
    }

    ModelProviderDto saveProviderForTenant(Long tenantId, SaveModelProviderCommand command) {
        requireNotBlank(command.providerName(), "供应商名称不能为空");
        var existing = providerRepository.findByName(tenantId, command.providerName());
        if (existing.isEmpty()) {
            requireNotBlank(command.apiKey(), "供应商 API Key 不能为空");
        }
        Long providerId = existing.map(ModelProvider::id).orElseGet(idGenerator::nextId);
        LocalDateTime now = LocalDateTime.now();
        ModelProvider provider = new ModelProvider(
                providerId,
                tenantId,
                command.providerName().trim(),
                command.providerType() == null ? ProviderType.OPENAI : command.providerType(),
                normalizeBaseUrl(command.baseUrl(), command.providerType()),
                resolveCiphertext(command.apiKey(), existing.orElse(null)),
                resolveLast4(command.apiKey(), existing.orElse(null)),
                STATUS_ENABLED,
                command.budgetUsd(),
                existing.map(ModelProvider::createdAt).orElse(now),
                now,
                buildConfiguredModels(tenantId, providerId, command, now));
        providerRepository.save(provider);
        return ModelProviderDtoMapper.toProviderDto(provider);
    }

    private List<ConfiguredModel> buildConfiguredModels(
            Long tenantId, Long providerId, SaveModelProviderCommand command, LocalDateTime now) {
        Map<String, ConfiguredModel> existingModels = providerRepository.listModels(tenantId, providerId).stream()
                .collect(Collectors.toMap(ConfiguredModel::identity, Function.identity(), (first, second) -> first));
        return (command.models() == null ? List.<SaveModelProviderCommand.ModelCommand>of() : command.models()).stream()
                .map(model -> {
                    ConfiguredModel existing = existingModels.get(model.modelCode() + "::" + model.capabilityType().name());
                    return new ConfiguredModel(
                            existing == null ? idGenerator.nextId() : existing.id(),
                            tenantId,
                            providerId,
                            model.modelCode(),
                            model.displayName(),
                            model.capabilityType(),
                            STATUS_ENABLED,
                            model.defaults(),
                            existing == null ? now : existing.createdAt(),
                            now);
                })
                .toList();
    }

    private String resolveCiphertext(String apiKey, ModelProvider existing) {
        return hasText(apiKey) ? providerSecretPort.encrypt(apiKey) : existing.apiKeyCiphertext();
    }

    private String resolveLast4(String apiKey, ModelProvider existing) {
        return hasText(apiKey) ? providerSecretPort.last4(apiKey) : existing.apiKeyLast4();
    }

    private String normalizeBaseUrl(String baseUrl, ProviderType providerType) {
        ProviderType effectiveType = providerType == null ? ProviderType.OPENAI : providerType;
        if (effectiveType == ProviderType.OPENAI && (baseUrl == null || baseUrl.isBlank() || "官方默认".equals(baseUrl))) {
            return "OFFICIAL_DEFAULT";
        }
        requireNotBlank(baseUrl, "供应商 baseUrl 不能为空");
        return baseUrl.trim();
    }

    private void requireNotBlank(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
