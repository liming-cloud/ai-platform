package org.fujay.ai.platform.model.application.service;

import java.util.Map;
import java.util.stream.Collectors;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformModelOptionCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformProviderTypeCommand;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ModelOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ProviderTypeOptionDto;
import org.fujay.ai.platform.model.application.port.CurrentPrincipalPort;
import org.fujay.ai.platform.model.application.port.DomainIdGeneratorPort;
import org.fujay.ai.platform.model.domain.aggregate.PlatformModelOption;
import org.fujay.ai.platform.model.domain.aggregate.PlatformProviderTypeOption;
import org.fujay.ai.platform.model.domain.repository.PlatformModelCatalogRepository;
import org.fujay.ai.platform.model.domain.service.DefaultPlatformModelCatalog;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelPlatformCatalogApplicationService {

    private static final String STATUS_ENABLED = "ENABLED";

    private final PlatformModelCatalogRepository catalogRepository;
    private final CurrentPrincipalPort currentPrincipalPort;
    private final DomainIdGeneratorPort idGenerator;

    public ModelPlatformCatalogApplicationService(
            PlatformModelCatalogRepository catalogRepository,
            CurrentPrincipalPort currentPrincipalPort,
            DomainIdGeneratorPort idGenerator) {
        this.catalogRepository = catalogRepository;
        this.currentPrincipalPort = currentPrincipalPort;
        this.idGenerator = idGenerator;
    }

    @Transactional(readOnly = true)
    public ModelProviderOptionsDto listProviderOptions() {
        currentPrincipalPort.requireAuthenticated();
        return ModelProviderDtoMapper.toOptionsDto(
                catalogRepository.listEnabledProviderTypes(), catalogRepository.listEnabledModelOptions());
    }

    @Transactional(readOnly = true)
    public ModelProviderOptionsDto listPlatformCatalog() {
        currentPrincipalPort.requireAuthenticated();
        return ModelProviderDtoMapper.toOptionsDto(
                catalogRepository.listAllProviderTypes(), catalogRepository.listAllModelOptions());
    }

    @Transactional
    public ProviderTypeOptionDto savePlatformProviderType(SavePlatformProviderTypeCommand command) {
        currentPrincipalPort.requireAuthenticated();
        return savePlatformProviderTypeInternal(command);
    }

    @Transactional
    public ModelOptionDto savePlatformModelOption(SavePlatformModelOptionCommand command) {
        currentPrincipalPort.requireAuthenticated();
        return savePlatformModelOptionInternal(command);
    }

    @Transactional
    public void bootstrapPlatformCatalog() {
        Map<ProviderType, Long> providerTypeIds = DefaultPlatformModelCatalog.providerTypes().stream()
                .collect(Collectors.toMap(DefaultPlatformModelCatalog.ProviderTypeSpec::providerType, spec -> {
                    ProviderTypeOptionDto option = savePlatformProviderTypeInternal(new SavePlatformProviderTypeCommand(
                            null, spec.providerType(), spec.displayName(), spec.defaultBaseUrl(), STATUS_ENABLED, spec.sortOrder()));
                    return option.providerTypeId();
                }));
        for (DefaultPlatformModelCatalog.ModelSpec spec : DefaultPlatformModelCatalog.models()) {
            savePlatformModelOptionInternal(new SavePlatformModelOptionCommand(
                    null, providerTypeIds.get(spec.providerType()), spec.modelCode(), spec.displayName(),
                    spec.capabilityType(), spec.defaults(), STATUS_ENABLED, spec.sortOrder()));
        }
    }

    private ProviderTypeOptionDto savePlatformProviderTypeInternal(SavePlatformProviderTypeCommand command) {
        PlatformProviderTypeOption option = buildProviderTypeOption(command);
        catalogRepository.saveProviderType(option);
        return ModelProviderDtoMapper.toProviderTypeOptionDto(option);
    }

    private ModelOptionDto savePlatformModelOptionInternal(SavePlatformModelOptionCommand command) {
        PlatformModelOption option = buildModelOption(command);
        catalogRepository.saveModelOption(option);
        return ModelProviderDtoMapper.toModelOptionDto(option);
    }

    private PlatformProviderTypeOption buildProviderTypeOption(SavePlatformProviderTypeCommand command) {
        ProviderType providerType = command.providerType() == null ? ProviderType.OPENAI_COMPATIBLE : command.providerType();
        Long id = catalogRepository.listAllProviderTypes().stream()
                .filter(option -> (command.providerTypeId() != null && option.id().equals(command.providerTypeId()))
                        || option.providerType() == providerType)
                .map(PlatformProviderTypeOption::id)
                .findFirst()
                .orElseGet(idGenerator::nextId);
        return new PlatformProviderTypeOption(id, providerType, command.displayName().trim(),
                command.defaultBaseUrl().trim(), normalizeStatus(command.status()), normalizeSortOrder(command.sortOrder()));
    }

    private PlatformModelOption buildModelOption(SavePlatformModelOptionCommand command) {
        if (command.providerTypeId() == null) {
            throw new IllegalArgumentException("供应商类型标识不能为空");
        }
        PlatformProviderTypeOption providerType = catalogRepository.listAllProviderTypes().stream()
                .filter(option -> option.id().equals(command.providerTypeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("供应商类型不存在"));
        ModelCapabilityType capabilityType = command.capabilityType() == null ? ModelCapabilityType.CHAT : command.capabilityType();
        Long id = catalogRepository.listAllModelOptions().stream()
                .filter(option -> (command.modelOptionId() != null && option.id().equals(command.modelOptionId()))
                        || (option.providerTypeId().equals(command.providerTypeId())
                                && option.modelCode().equals(command.modelCode().trim())
                                && option.capabilityType() == capabilityType))
                .map(PlatformModelOption::id)
                .findFirst()
                .orElseGet(idGenerator::nextId);
        PlatformModelOption option = new PlatformModelOption(id, command.providerTypeId(), command.modelCode().trim(),
                command.displayName().trim(), capabilityType, defaultIfNull(command.defaults()),
                normalizeStatus(command.status()), normalizeSortOrder(command.sortOrder()));
        option.validateFor(providerType.providerType());
        return option;
    }

    private ModelParameterDefaults defaultIfNull(ModelParameterDefaults defaults) {
        return defaults == null ? new ModelParameterDefaults(null, null, null, null, null, null, null, null, null) : defaults;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank() ? STATUS_ENABLED : status.trim().toUpperCase();
        if (!STATUS_ENABLED.equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("状态只能是 ENABLED 或 DISABLED");
        }
        return normalized;
    }

    private Integer normalizeSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return 100;
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("排序值不能小于 0");
        }
        return sortOrder;
    }
}
