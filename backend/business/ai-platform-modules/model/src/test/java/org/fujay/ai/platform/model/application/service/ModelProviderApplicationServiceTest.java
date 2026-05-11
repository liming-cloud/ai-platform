package org.fujay.ai.platform.model.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.fujay.ai.platform.model.application.command.SavePlatformModelOptionCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformProviderTypeCommand;
import org.fujay.ai.platform.ai.AiGatewayFacade;
import org.fujay.ai.platform.ai.ChatTestCommand;
import org.fujay.ai.platform.ai.ChatTestResult;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand.ModelCommand;
import org.fujay.ai.platform.model.application.command.TestModelCommand;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.security.AuthPrincipal;
import org.fujay.ai.platform.security.AuthPrincipalCodec;
import org.fujay.ai.platform.security.SecretCipher;
import org.fujay.ai.platform.security.SecuritySessionFacade;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.fujay.ai.platform.shared.tenant.TenantId;
import org.junit.jupiter.api.Test;

class ModelProviderApplicationServiceTest {

    @Test
    void savesOpenAiProviderWithEncryptedApiKeyAndChatCapability() {
        InMemoryRepository repository = new InMemoryRepository();
        ModelProviderApplicationService service = service(repository, command -> new ChatTestResult("ok", 1, 1, 2));

        var provider = service.saveProvider(new SaveModelProviderCommand(
                "OpenAI",
                ProviderType.OPENAI,
                "官方默认",
                "sk-test-secret",
                BigDecimal.valueOf(5),
                List.of(chatModel("o3"))));

        assertThat(provider.providerName()).isEqualTo("OpenAI");
        assertThat(provider.apiKeyLast4()).isEqualTo("cret");
        assertThat(provider.models()).hasSize(1);
        assertThat(repository.providers.getFirst().apiKeyCiphertext()).doesNotContain("sk-test-secret");
        assertThat(repository.models.getFirst().capabilityType()).isEqualTo(ModelCapabilityType.CHAT);
    }

    @Test
    void rejectsO3AsEmbeddingModelBeforeProviderCall() {
        InMemoryRepository repository = new InMemoryRepository();
        ModelProviderApplicationService service = service(repository, command -> new ChatTestResult("ok", 1, 1, 2));

        assertThatThrownBy(() -> service.saveProvider(new SaveModelProviderCommand(
                        "OpenAI",
                        ProviderType.OPENAI,
                        "官方默认",
                        "sk-test-secret",
                        BigDecimal.valueOf(5),
                        List.of(embeddingModel("o3")))))
                .isInstanceOf(PlatformException.class)
                .extracting("errorCode")
                .isEqualTo(ModelErrorCode.MODEL_CAPABILITY_MISMATCH);
    }

    @Test
    void testsChatModelThroughGatewayAndRecordsInvocation() {
        InMemoryRepository repository = new InMemoryRepository();
        ModelProviderApplicationService service = service(repository, command -> {
            assertThat(command.apiKey()).isEqualTo("sk-test-secret");
            assertThat(command.modelCode()).isEqualTo("o3");
            return new ChatTestResult("provider ok", 2, 3, 5);
        });
        var provider = service.saveProvider(new SaveModelProviderCommand(
                "OpenAI",
                ProviderType.OPENAI,
                "官方默认",
                "sk-test-secret",
                BigDecimal.valueOf(5),
                List.of(chatModel("o3"))));

        var result = service.testModel(new TestModelCommand(provider.models().getFirst().modelId(), "ping"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("provider ok");
        assertThat(repository.invocations).hasSize(1);
        assertThat(repository.invocations.getFirst().status()).isEqualTo("SUCCESS");
    }

    @Test
    void updatesExistingProviderWithoutRotatingModelIdOrRequiringApiKey() {
        InMemoryRepository repository = new InMemoryRepository();
        ModelProviderApplicationService service = service(repository, command -> new ChatTestResult("ok", 1, 1, 2));
        var created = service.saveProvider(new SaveModelProviderCommand(
                "SiliconFlow",
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.siliconflow.cn/v1",
                "sk-test-secret",
                BigDecimal.ZERO,
                List.of(chatModel("tencent/Hunyuan-MT-7B"))));

        var updated = service.saveProvider(new SaveModelProviderCommand(
                "SiliconFlow",
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.siliconflow.cn",
                null,
                BigDecimal.ZERO,
                List.of(chatModel("tencent/Hunyuan-MT-7B"))));

        assertThat(updated.providerId()).isEqualTo(created.providerId());
        assertThat(updated.apiKeyLast4()).isEqualTo(created.apiKeyLast4());
        assertThat(updated.models().getFirst().modelId()).isEqualTo(created.models().getFirst().modelId());
        assertThat(repository.models.getFirst().id()).isEqualTo(created.models().getFirst().modelId());
    }

    @Test
    void platformCatalogConfigurationBecomesTenantSelectableOption() {
        InMemoryRepository repository = new InMemoryRepository();
        ModelProviderApplicationService service = service(repository, command -> new ChatTestResult("ok", 1, 1, 2));

        var providerType = service.savePlatformProviderType(new SavePlatformProviderTypeCommand(
                null,
                ProviderType.OPENAI_COMPATIBLE,
                "硅基流动",
                "https://api.siliconflow.cn",
                "ENABLED",
                30));
        service.savePlatformModelOption(new SavePlatformModelOptionCommand(
                null,
                providerType.providerTypeId(),
                "Qwen/Qwen2.5-7B-Instruct",
                "Qwen2.5-7B 免费对话模型",
                ModelCapabilityType.CHAT,
                new ModelParameterDefaults(32768, 2048, null, BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.7), null, null, null, null),
                "ENABLED",
                31));

        var tenantOptions = service.listProviderOptions();

        assertThat(tenantOptions.providerTypes()).extracting("displayName").contains("硅基流动");
        assertThat(tenantOptions.modelOptions()).extracting("modelCode").contains("Qwen/Qwen2.5-7B-Instruct");
    }

    private ModelProviderApplicationService service(InMemoryRepository repository, AiGatewayFacade aiGatewayFacade) {
        SecuritySessionFacade sessionFacade = new SecuritySessionFacade(new AuthPrincipalCodec()) {
            @Override
            public AuthPrincipal currentPrincipal() {
                return new AuthPrincipal(new TenantId(1001L), "USER", 2001L);
            }
        };
        return new ModelProviderApplicationService(
                repository,
                sessionFacade,
                new SecretCipher("test-master-key-for-model-provider"),
                aiGatewayFacade);
    }

    private ModelCommand chatModel(String modelCode) {
        return new ModelCommand(
                modelCode,
                modelCode,
                ModelCapabilityType.CHAT,
                new ModelParameterDefaults(200000, 1024, null, null, null, null, null, null, "medium"));
    }

    private ModelCommand embeddingModel(String modelCode) {
        return new ModelCommand(
                modelCode,
                modelCode,
                ModelCapabilityType.EMBEDDING,
                new ModelParameterDefaults(null, null, 1536, null, null, null, null, null, null));
    }

    private static class InMemoryRepository implements ModelProviderRepository {
        private final List<ProviderRecord> providers = new ArrayList<>();
        private final List<ModelRecord> models = new ArrayList<>();
        private final List<InvocationRecord> invocations = new ArrayList<>();
        private final List<ProviderTypeOptionRecord> providerTypeOptions = new ArrayList<>();
        private final List<ModelOptionRecord> modelOptions = new ArrayList<>();

        @Override
        public void saveProvider(ProviderRecord provider, List<ModelRecord> models) {
            providers.removeIf(existing -> existing.id().equals(provider.id()));
            this.models.removeIf(existing -> existing.providerId().equals(provider.id()));
            providers.add(provider);
            this.models.addAll(models);
        }

        @Override
        public Optional<ProviderRecord> findProvider(Long tenantId, Long providerId) {
            return providers.stream()
                    .filter(provider -> provider.tenantId().equals(tenantId) && provider.id().equals(providerId))
                    .findFirst();
        }

        @Override
        public Optional<ProviderRecord> findProviderByName(Long tenantId, String providerName) {
            return providers.stream()
                    .filter(provider -> provider.tenantId().equals(tenantId)
                            && provider.providerName().equals(providerName))
                    .findFirst();
        }

        @Override
        public List<ProviderRecord> listProviders(Long tenantId) {
            return providers.stream().filter(provider -> provider.tenantId().equals(tenantId)).toList();
        }

        @Override
        public List<ProviderTypeOptionRecord> listProviderTypeOptions() {
            return providerTypeOptions.stream()
                    .filter(option -> "ENABLED".equals(option.status()))
                    .toList();
        }

        @Override
        public List<ModelOptionRecord> listModelOptions() {
            return modelOptions.stream()
                    .filter(option -> "ENABLED".equals(option.status()))
                    .toList();
        }

        @Override
        public List<ProviderTypeOptionRecord> listAllProviderTypeOptions() {
            return providerTypeOptions;
        }

        @Override
        public List<ModelOptionRecord> listAllModelOptions() {
            return modelOptions;
        }

        @Override
        public void saveProviderTypeOption(ProviderTypeOptionRecord option) {
            providerTypeOptions.removeIf(existing -> existing.id().equals(option.id())
                    || existing.providerType() == option.providerType());
            providerTypeOptions.add(option);
        }

        @Override
        public void saveModelOption(ModelOptionRecord option) {
            modelOptions.removeIf(existing -> existing.id().equals(option.id())
                    || (existing.providerTypeId().equals(option.providerTypeId())
                            && existing.modelCode().equals(option.modelCode())
                            && existing.capabilityType() == option.capabilityType()));
            modelOptions.add(option);
        }

        @Override
        public List<ModelRecord> listModels(Long tenantId, Long providerId) {
            return models.stream()
                    .filter(model -> model.tenantId().equals(tenantId) && model.providerId().equals(providerId))
                    .toList();
        }

        @Override
        public Optional<ModelRecord> findModel(Long tenantId, Long modelId) {
            return models.stream()
                    .filter(model -> model.tenantId().equals(tenantId) && model.id().equals(modelId))
                    .findFirst();
        }

        @Override
        public void appendInvocation(InvocationRecord invocation) {
            invocations.add(invocation);
        }
    }
}
