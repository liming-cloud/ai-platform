package org.fujay.ai.platform.model.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand;
import org.fujay.ai.platform.model.application.command.SaveModelProviderCommand.ModelCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformModelOptionCommand;
import org.fujay.ai.platform.model.application.command.SavePlatformProviderTypeCommand;
import org.fujay.ai.platform.model.application.command.TestModelCommand;
import org.fujay.ai.platform.model.application.port.CurrentPrincipalPort;
import org.fujay.ai.platform.model.application.port.DomainIdGeneratorPort;
import org.fujay.ai.platform.model.application.port.ModelGatewayChatRequest;
import org.fujay.ai.platform.model.application.port.ModelGatewayChatResult;
import org.fujay.ai.platform.model.application.port.ModelGatewayPort;
import org.fujay.ai.platform.model.application.port.ProviderSecretPort;
import org.fujay.ai.platform.model.domain.model.ConfiguredModel;
import org.fujay.ai.platform.model.domain.aggregate.ModelInvocation;
import org.fujay.ai.platform.model.domain.aggregate.ModelProvider;
import org.fujay.ai.platform.model.domain.aggregate.PlatformModelOption;
import org.fujay.ai.platform.model.domain.aggregate.PlatformProviderTypeOption;
import org.fujay.ai.platform.model.domain.repository.ModelInvocationRepository;
import org.fujay.ai.platform.model.domain.repository.ModelProviderRepository;
import org.fujay.ai.platform.model.domain.repository.PlatformModelCatalogRepository;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelErrorCode;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.error.PlatformException;
import org.junit.jupiter.api.Test;

class ModelProviderApplicationServiceTest {

    @Test
    void savesOpenAiProviderWithEncryptedApiKeyAndChatCapability() {
        TestContext context = context(request -> new ModelGatewayChatResult("ok", 1, 1, 2));

        var provider = context.service.saveProvider(new SaveModelProviderCommand(
                "OpenAI",
                ProviderType.OPENAI,
                "官方默认",
                "sk-test-secret",
                BigDecimal.valueOf(5),
                List.of(chatModel("o3"))));

        assertThat(provider.providerName()).isEqualTo("OpenAI");
        assertThat(provider.apiKeyLast4()).isEqualTo("cret");
        assertThat(provider.models()).hasSize(1);
        assertThat(context.providerRepository.providers.getFirst().apiKeyCiphertext()).isEqualTo("cipher:sk-test-secret");
        assertThat(context.providerRepository.providers.getFirst().models().getFirst().capabilityType())
                .isEqualTo(ModelCapabilityType.CHAT);
    }

    @Test
    void rejectsO3AsEmbeddingModelBeforeProviderCall() {
        TestContext context = context(request -> new ModelGatewayChatResult("ok", 1, 1, 2));

        assertThatThrownBy(() -> context.service.saveProvider(new SaveModelProviderCommand(
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
        TestContext context = context(request -> {
            assertThat(request.apiKeyCiphertext()).isEqualTo("cipher:sk-test-secret");
            assertThat(request.modelCode()).isEqualTo("o3");
            return new ModelGatewayChatResult("provider ok", 2, 3, 5);
        });
        var provider = context.service.saveProvider(new SaveModelProviderCommand(
                "OpenAI",
                ProviderType.OPENAI,
                "官方默认",
                "sk-test-secret",
                BigDecimal.valueOf(5),
                List.of(chatModel("o3"))));

        var result = context.service.testModel(new TestModelCommand(provider.models().getFirst().modelId(), "ping"));

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("provider ok");
        assertThat(context.invocationRepository.invocations).hasSize(1);
        assertThat(context.invocationRepository.invocations.getFirst().status()).isEqualTo("SUCCESS");
    }

    @Test
    void updatesExistingProviderWithoutRotatingModelIdOrRequiringApiKey() {
        TestContext context = context(request -> new ModelGatewayChatResult("ok", 1, 1, 2));
        var created = context.service.saveProvider(new SaveModelProviderCommand(
                "SiliconFlow",
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.siliconflow.cn/v1",
                "sk-test-secret",
                BigDecimal.ZERO,
                List.of(chatModel("tencent/Hunyuan-MT-7B"))));

        var updated = context.service.saveProvider(new SaveModelProviderCommand(
                "SiliconFlow",
                ProviderType.OPENAI_COMPATIBLE,
                "https://api.siliconflow.cn",
                null,
                BigDecimal.ZERO,
                List.of(chatModel("tencent/Hunyuan-MT-7B"))));

        assertThat(updated.providerId()).isEqualTo(created.providerId());
        assertThat(updated.apiKeyLast4()).isEqualTo(created.apiKeyLast4());
        assertThat(updated.models().getFirst().modelId()).isEqualTo(created.models().getFirst().modelId());
        assertThat(context.providerRepository.providers).hasSize(1);
    }

    @Test
    void platformCatalogConfigurationBecomesTenantSelectableOption() {
        TestContext context = context(request -> new ModelGatewayChatResult("ok", 1, 1, 2));

        var providerType = context.service.savePlatformProviderType(new SavePlatformProviderTypeCommand(
                null,
                ProviderType.OPENAI_COMPATIBLE,
                "硅基流动",
                "https://api.siliconflow.cn",
                "ENABLED",
                30));
        context.service.savePlatformModelOption(new SavePlatformModelOptionCommand(
                null,
                providerType.providerTypeId(),
                "Qwen/Qwen2.5-7B-Instruct",
                "Qwen2.5-7B 免费对话模型",
                ModelCapabilityType.CHAT,
                new ModelParameterDefaults(
                        32768, 2048, null, BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.7),
                        null, null, null, null),
                "ENABLED",
                31));

        var tenantOptions = context.service.listProviderOptions();

        assertThat(tenantOptions.providerTypes()).extracting("displayName").contains("硅基流动");
        assertThat(tenantOptions.modelOptions()).extracting("modelCode").contains("Qwen/Qwen2.5-7B-Instruct");
    }

    private static TestContext context(Function<ModelGatewayChatRequest, ModelGatewayChatResult> testHandler) {
        InMemoryModelProviderRepository providerRepository = new InMemoryModelProviderRepository();
        InMemoryPlatformModelCatalogRepository catalogRepository = new InMemoryPlatformModelCatalogRepository();
        InMemoryModelInvocationRepository invocationRepository = new InMemoryModelInvocationRepository();
        FixedPrincipal principal = new FixedPrincipal();
        FixedIdGenerator idGenerator = new FixedIdGenerator();
        StubSecretPort secretPort = new StubSecretPort();
        StubGatewayPort gatewayPort = new StubGatewayPort(testHandler);
        ModelProviderManagementApplicationService managementService =
                new ModelProviderManagementApplicationService(providerRepository, principal, secretPort, idGenerator);
        ModelPlatformCatalogApplicationService catalogService =
                new ModelPlatformCatalogApplicationService(catalogRepository, principal, idGenerator);
        ModelProviderTestApplicationService testService =
                new ModelProviderTestApplicationService(
                        providerRepository, invocationRepository, principal, gatewayPort, idGenerator);
        ModelProviderApplicationService service =
                new ModelProviderApplicationService(managementService, catalogService, testService);
        return new TestContext(service, providerRepository, invocationRepository);
    }

    private static ModelCommand chatModel(String modelCode) {
        return new ModelCommand(
                modelCode,
                modelCode,
                ModelCapabilityType.CHAT,
                new ModelParameterDefaults(200000, 1024, null, null, null, null, null, null, "medium"));
    }

    private static ModelCommand embeddingModel(String modelCode) {
        return new ModelCommand(
                modelCode,
                modelCode,
                ModelCapabilityType.EMBEDDING,
                new ModelParameterDefaults(null, null, 1536, null, null, null, null, null, null));
    }

    private record TestContext(
            ModelProviderApplicationService service,
            InMemoryModelProviderRepository providerRepository,
            InMemoryModelInvocationRepository invocationRepository) {
    }

    private static final class FixedPrincipal implements CurrentPrincipalPort {
        @Override
        public Long currentTenantId() {
            return 1001L;
        }

        @Override
        public void requireAuthenticated() {
        }
    }

    private static final class FixedIdGenerator implements DomainIdGeneratorPort {
        private final AtomicLong sequence = new AtomicLong(3000L);

        @Override
        public Long nextId() {
            return sequence.incrementAndGet();
        }
    }

    private static final class StubSecretPort implements ProviderSecretPort {
        @Override
        public String encrypt(String plaintext) {
            return "cipher:" + plaintext;
        }

        @Override
        public String last4(String plaintext) {
            return plaintext.substring(plaintext.length() - 4);
        }
    }

    private static final class StubGatewayPort implements ModelGatewayPort {
        private final Function<ModelGatewayChatRequest, ModelGatewayChatResult> testHandler;

        private StubGatewayPort(Function<ModelGatewayChatRequest, ModelGatewayChatResult> testHandler) {
            this.testHandler = testHandler;
        }

        @Override
        public ModelGatewayChatResult testChat(ModelGatewayChatRequest request) {
            return testHandler.apply(request);
        }

        @Override
        public ModelGatewayChatResult invokeChat(ModelGatewayChatRequest request) {
            throw new UnsupportedOperationException("provider tests use testChat only");
        }
    }

    private static final class InMemoryModelProviderRepository implements ModelProviderRepository {
        private final List<ModelProvider> providers = new ArrayList<>();

        @Override
        public void save(ModelProvider provider) {
            providers.removeIf(existing -> existing.id().equals(provider.id()));
            providers.add(provider);
        }

        @Override
        public Optional<ModelProvider> findById(Long tenantId, Long providerId) {
            return providers.stream()
                    .filter(provider -> provider.tenantId().equals(tenantId) && provider.id().equals(providerId))
                    .findFirst();
        }

        @Override
        public Optional<ModelProvider> findByName(Long tenantId, String providerName) {
            return providers.stream()
                    .filter(provider -> provider.tenantId().equals(tenantId)
                            && provider.providerName().equals(providerName))
                    .findFirst();
        }

        @Override
        public List<ModelProvider> listByTenant(Long tenantId) {
            return providers.stream().filter(provider -> provider.tenantId().equals(tenantId)).toList();
        }

        @Override
        public List<ConfiguredModel> listModels(Long tenantId, Long providerId) {
            return findById(tenantId, providerId).map(ModelProvider::models).orElse(List.of());
        }

        @Override
        public Optional<ConfiguredModel> findModel(Long tenantId, Long modelId) {
            return providers.stream()
                    .filter(provider -> provider.tenantId().equals(tenantId))
                    .flatMap(provider -> provider.models().stream())
                    .filter(model -> model.id().equals(modelId))
                    .findFirst();
        }
    }

    private static final class InMemoryPlatformModelCatalogRepository implements PlatformModelCatalogRepository {
        private final List<PlatformProviderTypeOption> providerTypes = new ArrayList<>();
        private final List<PlatformModelOption> modelOptions = new ArrayList<>();

        @Override
        public List<PlatformProviderTypeOption> listEnabledProviderTypes() {
            return providerTypes.stream().filter(option -> "ENABLED".equals(option.status())).toList();
        }

        @Override
        public List<PlatformModelOption> listEnabledModelOptions() {
            return modelOptions.stream().filter(option -> "ENABLED".equals(option.status())).toList();
        }

        @Override
        public List<PlatformProviderTypeOption> listAllProviderTypes() {
            return providerTypes;
        }

        @Override
        public List<PlatformModelOption> listAllModelOptions() {
            return modelOptions;
        }

        @Override
        public void saveProviderType(PlatformProviderTypeOption option) {
            providerTypes.removeIf(existing -> existing.id().equals(option.id())
                    || existing.providerType() == option.providerType());
            providerTypes.add(option);
        }

        @Override
        public void saveModelOption(PlatformModelOption option) {
            modelOptions.removeIf(existing -> existing.id().equals(option.id())
                    || (existing.providerTypeId().equals(option.providerTypeId())
                            && existing.modelCode().equals(option.modelCode())
                            && existing.capabilityType() == option.capabilityType()));
            modelOptions.add(option);
        }
    }

    private static final class InMemoryModelInvocationRepository implements ModelInvocationRepository {
        private final List<ModelInvocation> invocations = new ArrayList<>();

        @Override
        public void append(ModelInvocation invocation) {
            invocations.add(invocation);
        }
    }
}
