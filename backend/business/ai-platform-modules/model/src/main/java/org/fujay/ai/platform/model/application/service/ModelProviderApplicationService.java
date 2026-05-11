package org.fujay.ai.platform.model.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.fujay.ai.platform.ai.AiGatewayFacade;
import org.fujay.ai.platform.ai.AiProviderType;
import org.fujay.ai.platform.ai.ChatTestCommand;
import org.fujay.ai.platform.ai.ChatTestResult;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformModelOptionCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformProviderTypeCommand;
import org.fujay.ai.platform.model.application.command.TestModelCommand;
import org.fujay.ai.platform.model.application.dto.ModelDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ModelOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderOptionsDto.ProviderTypeOptionDto;
import org.fujay.ai.platform.model.application.dto.ModelProviderDto;
import org.fujay.ai.platform.model.application.dto.ModelTestDto;
import org.fujay.ai.platform.model.domain.aggregate.ModelProviderConfig;
import org.fujay.ai.platform.model.domain.aggregate.ModelProviderConfig.ModelDefinition;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository.InvocationRecord;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository.ModelOptionRecord;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository.ModelRecord;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository.ProviderRecord;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository.ProviderTypeOptionRecord;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.security.AuthPrincipal;
import org.fujay.ai.platform.security.SecretCipher;
import org.fujay.ai.platform.security.SecuritySessionFacade;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelProviderApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelProviderApplicationService.class);
    private static final String STATUS_ENABLED = "ENABLED";

    private final ModelProviderRepository repository;
    private final SecuritySessionFacade sessionFacade;
    private final SecretCipher secretCipher;
    private final AiGatewayFacade aiGatewayFacade;

    public ModelProviderApplicationService(
            ModelProviderRepository repository,
            SecuritySessionFacade sessionFacade,
            SecretCipher secretCipher,
            AiGatewayFacade aiGatewayFacade) {
        this.repository = repository;
        this.sessionFacade = sessionFacade;
        this.secretCipher = secretCipher;
        this.aiGatewayFacade = aiGatewayFacade;
    }

    @Transactional
    public ModelProviderDto saveProvider(SaveModelProviderCommand command) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> doSaveProvider(principal.tenantId().value(), command));
    }

    @Transactional(readOnly = true)
    public List<ModelProviderDto> listProviders() {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> repository.listProviders(principal.tenantId().value()).stream()
                .map(provider -> toProviderDto(provider, repository.listModels(provider.tenantId(), provider.id())))
                .toList());
    }

    @Transactional(readOnly = true)
    public ModelProviderOptionsDto listProviderOptions() {
        sessionFacade.currentPrincipal();
        return toOptionsDto(repository.listProviderTypeOptions(), repository.listModelOptions());
    }

    @Transactional(readOnly = true)
    public ModelProviderOptionsDto listPlatformCatalog() {
        sessionFacade.currentPrincipal();
        return toOptionsDto(repository.listAllProviderTypeOptions(), repository.listAllModelOptions());
    }

    @Transactional
    public ProviderTypeOptionDto savePlatformProviderType(SavePlatformProviderTypeCommand command) {
        sessionFacade.currentPrincipal();
        ProviderType providerType = command.providerType() == null ? ProviderType.OPENAI_COMPATIBLE : command.providerType();
        requireNotBlank(command.displayName(), "供应商类型名称不能为空");
        requireNotBlank(command.defaultBaseUrl(), "供应商默认 baseUrl 不能为空");
        LocalDateTime now = LocalDateTime.now();
        ProviderTypeOptionRecord existing = repository.listAllProviderTypeOptions().stream()
                .filter(option -> (command.providerTypeId() != null && option.id().equals(command.providerTypeId()))
                        || option.providerType() == providerType)
                .findFirst()
                .orElse(null);
        ProviderTypeOptionRecord record = new ProviderTypeOptionRecord(
                existing == null ? newId() : existing.id(),
                providerType,
                command.displayName().trim(),
                command.defaultBaseUrl().trim(),
                normalizeStatus(command.status()),
                normalizeSortOrder(command.sortOrder()));
        repository.saveProviderTypeOption(record);
        LOGGER.info("平台模型供应商类型已保存：type={} id={} at={}", providerType, record.id(), now);
        return toProviderTypeOptionDto(record);
    }

    @Transactional
    public ModelOptionDto savePlatformModelOption(SavePlatformModelOptionCommand command) {
        sessionFacade.currentPrincipal();
        requireNotBlank(command.modelCode(), "模型编码不能为空");
        requireNotBlank(command.displayName(), "模型显示名称不能为空");
        if (command.providerTypeId() == null) {
            throw new IllegalArgumentException("供应商类型标识不能为空");
        }
        ProviderTypeOptionRecord providerType = repository.listAllProviderTypeOptions().stream()
                .filter(option -> option.id().equals(command.providerTypeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("供应商类型不存在"));
        ModelCapabilityType capabilityType =
                command.capabilityType() == null ? ModelCapabilityType.CHAT : command.capabilityType();
        ModelParameterDefaults defaults = command.defaults() == null
                ? new ModelParameterDefaults(null, null, null, null, null, null, null, null, null)
                : command.defaults();
        defaults.validate(providerType.providerType(), capabilityType);
        ModelOptionRecord existing = repository.listAllModelOptions().stream()
                .filter(option -> (command.modelOptionId() != null && option.id().equals(command.modelOptionId()))
                        || (option.providerTypeId().equals(command.providerTypeId())
                                && option.modelCode().equals(command.modelCode().trim())
                                && option.capabilityType() == capabilityType))
                .findFirst()
                .orElse(null);
        ModelOptionRecord record = new ModelOptionRecord(
                existing == null ? newId() : existing.id(),
                command.providerTypeId(),
                command.modelCode().trim(),
                command.displayName().trim(),
                capabilityType,
                defaults,
                normalizeStatus(command.status()),
                normalizeSortOrder(command.sortOrder()));
        repository.saveModelOption(record);
        return toModelOptionDto(record);
    }

    @Transactional(noRollbackFor = PlatformException.class)
    public ModelTestDto testModel(TestModelCommand command) {
        AuthPrincipal principal = sessionFacade.currentPrincipal();
        return TenantContext.callWith(principal.tenantId(), () -> doTestModel(principal.tenantId().value(), command));
    }

    private ModelProviderDto doSaveProvider(Long tenantId, SaveModelProviderCommand command) {
        requireNotBlank(command.providerName(), "供应商名称不能为空");
        var existingProvider = repository.findProviderByName(tenantId, command.providerName());
        if (existingProvider.isEmpty()) {
            requireNotBlank(command.apiKey(), "供应商 API Key 不能为空");
        }
        ProviderType providerType = command.providerType() == null ? ProviderType.OPENAI : command.providerType();
        Long providerId = existingProvider
                .map(ProviderRecord::id)
                .orElseGet(this::newId);
        LocalDateTime now = LocalDateTime.now();
        String ciphertext = hasText(command.apiKey())
                ? secretCipher.encrypt(command.apiKey())
                : existingProvider.map(ProviderRecord::apiKeyCiphertext).orElse(null);
        String apiKeyLast4 = hasText(command.apiKey())
                ? secretCipher.last4(command.apiKey())
                : existingProvider.map(ProviderRecord::apiKeyLast4).orElse(null);
        ProviderRecord provider = new ProviderRecord(
                providerId,
                tenantId,
                command.providerName(),
                providerType,
                normalizeBaseUrl(command.baseUrl(), providerType),
                ciphertext,
                apiKeyLast4,
                STATUS_ENABLED,
                command.budgetUsd(),
                existingProvider.map(ProviderRecord::createdAt).orElse(now),
                now);
        Map<String, ModelRecord> existingModels = repository.listModels(tenantId, providerId).stream()
                .collect(Collectors.toMap(this::modelIdentity, Function.identity(), (first, second) -> first));
        List<ModelRecord> models = (command.models() == null ? List.<SaveModelProviderCommand.ModelCommand>of() : command.models())
                .stream()
                .map(model -> {
                    ModelRecord existingModel = existingModels.get(modelIdentity(model.modelCode(), model.capabilityType()));
                    return new ModelRecord(
                            existingModel == null ? newId() : existingModel.id(),
                            tenantId,
                            providerId,
                            model.modelCode(),
                            model.displayName(),
                            model.capabilityType(),
                            STATUS_ENABLED,
                            model.defaults(),
                            existingModel == null ? now : existingModel.createdAt(),
                            now);
                })
                .toList();
        validate(provider, models);
        repository.saveProvider(provider, models);
        return toProviderDto(provider, models);
    }

    private String modelIdentity(ModelRecord model) {
        return modelIdentity(model.modelCode(), model.capabilityType());
    }

    private String modelIdentity(String modelCode, ModelCapabilityType capabilityType) {
        return modelCode + "::" + capabilityType.name();
    }

    private ModelTestDto doTestModel(Long tenantId, TestModelCommand command) {
        ModelRecord model = repository
                .findModel(tenantId, command.modelId())
                .orElseThrow(() -> new PlatformException(ModelErrorCode.MODEL_NOT_FOUND));
        ProviderRecord provider = repository
                .findProvider(tenantId, model.providerId())
                .orElseThrow(() -> new PlatformException(ModelErrorCode.PROVIDER_NOT_FOUND));
        if (model.capabilityType() != ModelCapabilityType.CHAT) {
            throw new PlatformException(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);
        }

        long startedAt = System.nanoTime();
        try {
            ChatTestResult result = aiGatewayFacade.testChat(new ChatTestCommand(
                    toAiProviderType(provider.providerType()),
                    provider.baseUrl(),
                    secretCipher.decrypt(provider.apiKeyCiphertext()),
                    model.modelCode(),
                    normalizePrompt(command.prompt()),
                    model.defaults().maxOutputTokens(),
                    model.defaults().temperature(),
                    model.defaults().topP(),
                    model.defaults().frequencyPenalty(),
                    model.defaults().presencePenalty(),
                    model.defaults().reasoningEffort()));
            long latencyMs = elapsedMs(startedAt);
            repository.appendInvocation(successInvocation(tenantId, provider, model, latencyMs, result));
            return new ModelTestDto(
                    true,
                    provider.id(),
                    model.id(),
                    provider.providerName(),
                    model.modelCode(),
                    model.capabilityType(),
                    latencyMs,
                    result.message());
        } catch (RuntimeException ex) {
            long latencyMs = elapsedMs(startedAt);
            LOGGER.warn("模型供应商连接测试失败：{}", ex.getMessage());
            repository.appendInvocation(failedInvocation(tenantId, provider, model, latencyMs, ex));
            throw new PlatformException(ModelErrorCode.PROVIDER_TEST_FAILED);
        }
    }

    private void validate(ProviderRecord provider, List<ModelRecord> models) {
        new ModelProviderConfig(
                provider.id(),
                provider.tenantId(),
                provider.providerName(),
                provider.providerType(),
                provider.baseUrl(),
                provider.apiKeyCiphertext(),
                provider.apiKeyLast4(),
                provider.budgetUsd(),
                models.stream()
                        .map(model -> new ModelDefinition(
                                model.id(),
                                model.modelCode(),
                                model.displayName(),
                                model.capabilityType(),
                                model.defaults()))
                        .toList());
    }

    private ModelProviderDto toProviderDto(ProviderRecord provider, List<ModelRecord> models) {
        return new ModelProviderDto(
                provider.id(),
                provider.providerName(),
                provider.providerType(),
                provider.baseUrl(),
                provider.apiKeyLast4(),
                provider.status(),
                provider.budgetUsd(),
                models.stream().map(this::toModelDto).toList());
    }

    private ModelProviderOptionsDto toOptionsDto(
            List<ProviderTypeOptionRecord> providerTypes, List<ModelOptionRecord> modelOptions) {
        return new ModelProviderOptionsDto(
                providerTypes.stream().map(this::toProviderTypeOptionDto).toList(),
                modelOptions.stream().map(this::toModelOptionDto).toList());
    }

    private ProviderTypeOptionDto toProviderTypeOptionDto(ProviderTypeOptionRecord option) {
        return new ProviderTypeOptionDto(
                option.id(),
                option.providerType(),
                option.displayName(),
                option.defaultBaseUrl(),
                option.status(),
                option.sortOrder());
    }

    private ModelOptionDto toModelOptionDto(ModelOptionRecord option) {
        return new ModelOptionDto(
                option.id(),
                option.providerTypeId(),
                option.modelCode(),
                option.displayName(),
                option.capabilityType(),
                option.defaults(),
                option.status(),
                option.sortOrder());
    }

    private ModelDto toModelDto(ModelRecord model) {
        return new ModelDto(
                model.id(),
                model.providerId(),
                model.modelCode(),
                model.displayName(),
                model.capabilityType(),
                model.status(),
                model.defaults());
    }

    private InvocationRecord successInvocation(
            Long tenantId, ProviderRecord provider, ModelRecord model, long latencyMs, ChatTestResult result) {
        return new InvocationRecord(
                newId(),
                tenantId,
                provider.id(),
                model.id(),
                provider.providerType(),
                model.modelCode(),
                model.capabilityType(),
                "SUCCESS",
                latencyMs,
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens(),
                null,
                null,
                LocalDateTime.now());
    }

    private InvocationRecord failedInvocation(
            Long tenantId, ProviderRecord provider, ModelRecord model, long latencyMs, RuntimeException ex) {
        String message = ex.getMessage() == null ? "供应商调用失败" : ex.getMessage();
        return new InvocationRecord(
                newId(),
                tenantId,
                provider.id(),
                model.id(),
                provider.providerType(),
                model.modelCode(),
                model.capabilityType(),
                "FAILED",
                latencyMs,
                null,
                null,
                null,
                ModelErrorCode.PROVIDER_TEST_FAILED.code(),
                message.length() > 512 ? message.substring(0, 512) : message,
                LocalDateTime.now());
    }

    private String normalizeBaseUrl(String baseUrl, ProviderType providerType) {
        if (providerType == ProviderType.OPENAI && (baseUrl == null || baseUrl.isBlank() || "官方默认".equals(baseUrl))) {
            return "OFFICIAL_DEFAULT";
        }
        requireNotBlank(baseUrl, "供应商 baseUrl 不能为空");
        return baseUrl.trim();
    }

    private AiProviderType toAiProviderType(ProviderType providerType) {
        return AiProviderType.valueOf(providerType.name());
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "请用一句话回复：AI Platform provider test ok";
        }
        return prompt;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_ENABLED;
        }
        String normalized = status.trim().toUpperCase();
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

    private long elapsedMs(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private Long newId() {
        return SnowflakeIdGenerator.nextId();
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
