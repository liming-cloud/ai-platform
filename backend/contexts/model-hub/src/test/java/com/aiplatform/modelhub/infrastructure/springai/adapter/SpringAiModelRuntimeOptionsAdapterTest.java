package com.aiplatform.modelhub.infrastructure.springai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.springai.model.SpringAiModelRuntimeGateway;
import com.aiplatform.infra.springai.model.provider.azureopenai.strategy.AzureOpenAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.bedrock.strategy.BedrockSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.local.LocalDeterministicChatModelFactory;
import com.aiplatform.infra.springai.model.provider.local.LocalDeterministicEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.provider.local.LocalDeterministicSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.local.LocalDeterministicSpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.provider.mistral.strategy.MistralAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.openai.OpenAiCompatibleSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.provider.vertexai.strategy.VertexAiSpringAiModelOptionsStrategy;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import com.aiplatform.infra.springai.model.strategy.StandardSpringAiModelOptionsStrategy;
import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConfigStatus;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelProviderStatus;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.ModelRuntimeValidation;
import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.ModelType;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelRuntimeAuthorityDigestMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SpringAiModelRuntimeOptionsAdapterTest {
    @Test
    void validatesProviderKindAsRuntimeSelectorInsteadOfModelOption() {
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        openAiOptionsRegistry(),
                        List.of(noopProbe())
                ),
                unusedSecretResolver(),
                authorityDigest()
        );

        ModelRuntimeValidation validation = adapter.validate(provider(), config(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.2
        )));

        assertThat(validation.valid()).isTrue();
        assertThat(validation.violations()).isEmpty();
    }

    @Test
    void validatesSecretBackedProviderStructureWithoutResolvingCredentialMaterial() {
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        new SpringAiModelOptionsRegistry(List.of(
                                new MistralAiSpringAiModelOptionsStrategy(),
                                new AzureOpenAiSpringAiModelOptionsStrategy(),
                                new BedrockSpringAiModelOptionsStrategy(),
                                new VertexAiSpringAiModelOptionsStrategy()
                        )),
                        List.of()
                ),
                unusedSecretResolver(),
                authorityDigest()
        );

        assertThat(adapter.validate(
                provider("mistral", "https://api.mistral.ai/v1", "api_key"),
                config(
                        "mistral-small-latest",
                        ModelType.CHAT,
                        null,
                        Map.of("providerKind", "mistral-ai", "maxTokens", 1024)
                )
        ).valid()).as("Mistral AI 配置结构校验").isTrue();
        assertThat(adapter.validate(
                provider("azure-openai", "https://aip-test.openai.azure.com", "api_key"),
                config(
                        "gpt4o-prod",
                        ModelType.CHAT,
                        null,
                        Map.of(
                                "providerKind", "azure-openai",
                                "deploymentName", "gpt4o-prod",
                                "apiVersion", "2024-06-01"
                        )
                )
        ).valid()).as("Azure OpenAI 配置结构校验").isTrue();
        assertThat(adapter.validate(
                provider(
                        "bedrock",
                        "https://bedrock-runtime.us-east-1.amazonaws.com",
                        "aws_credentials"
                ),
                config(
                        "anthropic.claude-3-5-sonnet-20240620-v1:0",
                        ModelType.CHAT,
                        null,
                        Map.of(
                                "providerKind", "bedrock",
                                "region", "us-east-1",
                                "timeoutSeconds", 15
                        )
                )
        ).valid()).as("Amazon Bedrock 配置结构校验").isTrue();
        assertThat(adapter.validate(
                provider(
                        "vertex",
                        "https://us-central1-aiplatform.googleapis.com",
                        "gcp_service_account"
                ),
                config(
                        "text-embedding-004",
                        ModelType.EMBEDDING,
                        768,
                        Map.of(
                                "providerKind", "vertex-ai",
                                "projectId", "aip-test-project",
                                "location", "us-central1",
                                "publisher", "google",
                                "dimensions", 768
                        )
                )
        ).valid()).as("Vertex AI 配置结构校验").isTrue();
    }

    @Test
    void rejectsMissingProviderKindBeforeRuntimeValidation() {
        assertThatThrownBy(() -> config(Map.of("temperature", 0.2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerKind");
    }

    @Test
    void testConnectivityResolvesCredentialByProviderSecretReference() {
        AtomicReference<SpringAiModelRuntimeProbe> capturedProbe = new AtomicReference<>();
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        openAiOptionsRegistry(),
                        List.of(recordingProbe(capturedProbe))
                ),
                request -> {
                    assertThat(request.tenantId()).isEqualTo("2001");
                    assertThat(request.modelConfigId()).isEqualTo("4001");
                    assertThat(request.providerId()).isEqualTo("3001");
                    assertThat(request.providerCode()).isEqualTo("openai");
                    assertThat(request.providerKind()).isEqualTo("openai-compatible");
                    assertThat(request.endpoint()).isEqualTo("https://api.openai.example/v1");
                    assertThat(request.authType()).isEqualTo("api_key");
                    assertThat(request.modelCode()).isEqualTo("gpt-4.1-mini");
                    assertThat(request.modelConfigVersion()).isZero();
                    assertThat(request.modelType()).isEqualTo("CHAT");
                    assertThat(request.effectiveOptions()).containsEntry("temperature", 0.2);
                    assertThat(request.effectiveOptionsDigest()).isEqualTo("a".repeat(64));
                    assertThat(request.secretRefId()).isEqualTo("9001");
                    return "sk-live-secret";
                },
                authorityDigest()
        );

        ModelRuntimeConnectivity connectivity = adapter.testConnectivity(provider(), config(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.2
        )));

        assertThat(connectivity.connected()).isTrue();
        assertThat(connectivity.status()).isEqualTo(ModelConnectivityStatus.PASSED);
        assertThat(capturedProbe.get()).isNotNull();
        assertThat(capturedProbe.get().request().credentialMaterial()).isEqualTo("sk-live-secret");
    }

    @Test
    void testConnectivityNormalizesResolvedCredentialMaterialBeforeRuntimeProbe() {
        AtomicReference<SpringAiModelRuntimeProbe> capturedProbe = new AtomicReference<>();
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        openAiOptionsRegistry(),
                        List.of(recordingProbe(capturedProbe))
                ),
                request -> " sk-live-secret ",
                authorityDigest()
        );

        ModelRuntimeConnectivity connectivity = adapter.testConnectivity(provider(), config(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.2
        )));

        assertThat(connectivity.connected()).isTrue();
        assertThat(connectivity.status()).isEqualTo(ModelConnectivityStatus.PASSED);
        assertThat(capturedProbe.get()).isNotNull();
        assertThat(capturedProbe.get().request().credentialMaterial()).isEqualTo("sk-live-secret");
    }

    @Test
    void testConnectivityFailsClosedWhenSecretReferenceCannotBeResolved() {
        AtomicBoolean probeCalled = new AtomicBoolean(false);
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        openAiOptionsRegistry(),
                        List.of(new SpringAiModelProbeClient() {
                            @Override
                            public boolean supports(SpringAiModelRuntimeProbe request) {
                                return true;
                            }

                            @Override
                            public SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe request) {
                                probeCalled.set(true);
                                return SpringAiModelConnectivityResult.passed("不应调用模型供应商");
                            }
                        })
                ),
                request -> {
                    throw new IllegalStateException("模型供应商凭证材料引用不存在或未启用");
                },
                authorityDigest()
        );

        ModelRuntimeConnectivity connectivity = adapter.testConnectivity(provider(), config(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.2
        )));

        assertThat(connectivity.connected()).isFalse();
        assertThat(connectivity.status()).isEqualTo(ModelConnectivityStatus.FAILED);
        assertThat(connectivity.message()).contains("模型供应商凭证材料引用不存在或未启用");
        assertThat(probeCalled).isFalse();
    }

    @Test
    void testConnectivityResolvesAuthorityForNoAuthProvider() {
        AtomicBoolean authorityResolved = new AtomicBoolean(false);
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        openAiOptionsRegistry(),
                        List.of(recordingProbe(new AtomicReference<>()))
                ),
                request -> {
                    authorityResolved.set(true);
                    assertThat(request.authType()).isEqualTo("no_auth");
                    assertThat(request.secretRefId()).isNull();
                    return null;
                },
                authorityDigest()
        );

        ModelRuntimeConnectivity connectivity = adapter.testConnectivity(noAuthProvider(), config(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.2
        )));

        assertThat(connectivity.connected()).isTrue();
        assertThat(connectivity.status()).isEqualTo(ModelConnectivityStatus.PASSED);
        assertThat(authorityResolved).isTrue();
    }

    @Test
    void noAuthAuthorityRejectionPreventsRuntimeProbe() {
        AtomicBoolean probeCalled = new AtomicBoolean(false);
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        openAiOptionsRegistry(),
                        List.of(new SpringAiModelProbeClient() {
                            @Override
                            public boolean supports(SpringAiModelRuntimeProbe request) {
                                return true;
                            }

                            @Override
                            public SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe request) {
                                probeCalled.set(true);
                                return SpringAiModelConnectivityResult.passed("不应调用模型供应商");
                            }
                        })
                ),
                request -> {
                    throw new IllegalStateException("模型运行时 authority 校验失败");
                },
                authorityDigest()
        );

        ModelRuntimeConnectivity connectivity = adapter.testConnectivity(noAuthProvider(), config(Map.of(
                "providerKind", "openai-compatible",
                "temperature", 0.2
        )));

        assertThat(connectivity.connected()).isFalse();
        assertThat(connectivity.message()).contains("authority 校验失败");
        assertThat(probeCalled).isFalse();
    }

    @Test
    void validatesAndProbesLocalChatAndEmbeddingThroughTheModelHubRuntimeAdapter() {
        LocalDeterministicEmbeddingModelFactory embeddingFactory =
                new LocalDeterministicEmbeddingModelFactory();
        LocalDeterministicChatModelFactory chatFactory = new LocalDeterministicChatModelFactory();
        SpringAiModelRuntimeOptionsAdapter adapter = new SpringAiModelRuntimeOptionsAdapter(
                new SpringAiModelRuntimeGateway(
                        new SpringAiModelOptionsRegistry(List.of(
                                new StandardSpringAiModelOptionsStrategy(),
                                new LocalDeterministicSpringAiModelOptionsStrategy()
                        )),
                        List.of(new LocalDeterministicSpringAiModelProbeClient(embeddingFactory, chatFactory))
                ),
                request -> null,
                authorityDigest()
        );
        ModelConfig chat = config(
                "local-chat",
                ModelType.CHAT,
                null,
                Map.of("providerKind", "local-deterministic", "maxTokens", 16)
        );
        ModelConfig embedding = config(
                "local-bge-m3",
                ModelType.EMBEDDING,
                64,
                Map.of("providerKind", "local-deterministic")
        );
        ModelConfig unsupportedReasoning = config(
                "local-reasoning",
                ModelType.REASONING,
                null,
                Map.of("providerKind", "local-deterministic")
        );

        assertThat(adapter.validate(localDeterministicProvider(), chat).valid()).isTrue();
        assertThat(adapter.validate(localDeterministicProvider(), embedding).valid()).isTrue();
        assertThat(adapter.validate(localDeterministicProvider(), unsupportedReasoning).valid()).isFalse();
        assertThat(adapter.testConnectivity(localDeterministicProvider(), chat).status())
                .isEqualTo(ModelConnectivityStatus.PASSED);
        assertThat(adapter.testConnectivity(localDeterministicProvider(), embedding).status())
                .isEqualTo(ModelConnectivityStatus.PASSED);
    }

    private static ModelProvider provider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "openai",
                "OpenAI 企业账号",
                "https://api.openai.example/v1",
                "api_key",
                new ModelSecretRef("9001", "sk-***cret"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelProvider provider(String providerCode, String endpoint, String authType) {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                providerCode,
                providerCode + " 企业账号",
                endpoint,
                authType,
                new ModelSecretRef("9001", "credential-***"),
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelProvider noAuthProvider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "local-llm",
                "本地模型",
                "http://localhost:11434",
                "no_auth",
                null,
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelProvider localDeterministicProvider() {
        return new ModelProvider(
                TenantId.of("2001"),
                "3001",
                "local-deterministic",
                "本地确定性模型",
                "local://deterministic",
                "no_auth",
                null,
                ModelProviderStatus.ACTIVE,
                "REQ-TEST",
                true,
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static ModelConfig config(Map<String, Object> options) {
        return config("gpt-4.1-mini", ModelType.CHAT, null, options);
    }

    private static ModelConfig config(
            String modelCode,
            ModelType modelType,
            Integer embeddingDimension,
            Map<String, Object> options
    ) {
        return new ModelConfig(
                TenantId.of("2001"),
                "4001",
                "3001",
                modelCode,
                modelCode,
                modelType,
                128000,
                embeddingDimension,
                new BigDecimal("0.0004"),
                new BigDecimal("0.0016"),
                ModelConfigStatus.ACTIVE,
                "REQ-TEST",
                new ModelRuntimeOptions(options),
                ModelConnectivityStatus.NOT_TESTED,
                null,
                "",
                Instant.parse("2026-05-25T02:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z")
        );
    }

    private static SpringAiModelOptionsRegistry openAiOptionsRegistry() {
        return new SpringAiModelOptionsRegistry(List.of(
                new OpenAiCompatibleSpringAiModelOptionsStrategy(),
                new StandardSpringAiModelOptionsStrategy()
        ));
    }

    private static SpringAiConnectivityCredentialResolver unusedSecretResolver() {
        return request -> {
            throw new AssertionError("validate 不应解析凭证材料");
        };
    }

    private static ModelRuntimeAuthorityDigest authorityDigest() {
        ModelRuntimeAuthorityDigestMapper mapper = mock(ModelRuntimeAuthorityDigestMapper.class);
        when(mapper.canonicalEffectiveOptionsDigest(anyString())).thenReturn("a".repeat(64));
        return new ModelRuntimeAuthorityDigest(mapper, new JsonMapper());
    }

    private static SpringAiModelProbeClient noopProbe() {
        return recordingProbe(new AtomicReference<>());
    }

    private static SpringAiModelProbeClient recordingProbe(AtomicReference<SpringAiModelRuntimeProbe> capturedProbe) {
        return new SpringAiModelProbeClient() {
            @Override
            public boolean supports(SpringAiModelRuntimeProbe request) {
                return "openai-compatible".equals(request.request().providerKind());
            }

            @Override
            public SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe request) {
                capturedProbe.set(request);
                return SpringAiModelConnectivityResult.passed("模型运行时探测通过");
            }
        };
    }
}
