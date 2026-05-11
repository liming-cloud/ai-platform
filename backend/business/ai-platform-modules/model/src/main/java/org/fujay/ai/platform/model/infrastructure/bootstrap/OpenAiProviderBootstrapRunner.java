package org.fujay.ai.platform.model.infrastructure.bootstrap;

import java.time.LocalDateTime;
import java.util.List;
import org.fujay.ai.platform.model.domain.aggregate.ModelProviderConfig;
import org.fujay.ai.platform.model.domain.aggregate.ModelProviderConfig.ModelDefinition;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository.ModelRecord;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository.ProviderRecord;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.security.SecretCipher;
import org.fujay.ai.platform.shared.id.SnowflakeIdGenerator;
import org.fujay.ai.platform.shared.tenant.TenantContext;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.fujay.ai.platform.shared.tenant.TenantIdentityResolver;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class OpenAiProviderBootstrapRunner implements ApplicationRunner {

    private static final String STATUS_ENABLED = "ENABLED";

    private final OpenAiProviderBootstrapProperties properties;
    private final ModelProviderRepository repository;
    private final SecretCipher secretCipher;
    private final TenantIdentityResolver tenantIdentityResolver;

    public OpenAiProviderBootstrapRunner(
            OpenAiProviderBootstrapProperties properties,
            ModelProviderRepository repository,
            SecretCipher secretCipher,
            TenantIdentityResolver tenantIdentityResolver) {
        this.properties = properties;
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.tenantIdentityResolver = tenantIdentityResolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            return;
        }
        TenantId tenantId = tenantIdentityResolver
                .findTenantIdByCode(properties.tenantCode())
                .orElseThrow(() -> new IllegalStateException("未找到模型启动配置对应的租户编码：" + properties.tenantCode()));
        TenantContext.runWith(tenantId, () -> {
            if (repository.findProviderByName(tenantId.value(), properties.providerName()).isPresent()) {
                return;
            }
            saveOpenAiProvider(tenantId.value());
        });
    }

    private void saveOpenAiProvider(Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        Long providerId = SnowflakeIdGenerator.nextId();
        Long modelId = SnowflakeIdGenerator.nextId();
        ProviderRecord provider = new ProviderRecord(
                providerId,
                tenantId,
                properties.providerName(),
                ProviderType.OPENAI,
                "OFFICIAL_DEFAULT",
                secretCipher.encrypt(properties.apiKey()),
                secretCipher.last4(properties.apiKey()),
                STATUS_ENABLED,
                properties.budgetUsd(),
                now,
                now);
        ModelParameterDefaults defaults = new ModelParameterDefaults(
                properties.contextWindow(),
                properties.maxOutputTokens(),
                null,
                null,
                null,
                null,
                null,
                null,
                properties.reasoningEffort());
        ModelRecord chatModel = new ModelRecord(
                modelId,
                tenantId,
                providerId,
                properties.chatModel(),
                properties.chatModel(),
                ModelCapabilityType.CHAT,
                STATUS_ENABLED,
                defaults,
                now,
                now);
        validate(provider, chatModel);
        repository.saveProvider(provider, List.of(chatModel));
    }

    private void validate(ProviderRecord provider, ModelRecord model) {
        new ModelProviderConfig(
                provider.id(),
                provider.tenantId(),
                provider.providerName(),
                provider.providerType(),
                provider.baseUrl(),
                provider.apiKeyCiphertext(),
                provider.apiKeyLast4(),
                provider.budgetUsd(),
                List.of(new ModelDefinition(
                        model.id(),
                        model.modelCode(),
                        model.displayName(),
                        model.capabilityType(),
                        model.defaults())));
    }
}
