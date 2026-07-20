package com.aiplatform.modelhub.infrastructure.springai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlConfigPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlProviderPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlConfigMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlProviderMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelRuntimeAuthorityDigestMapper;
import com.aiplatform.modelhub.infrastructure.secret.ModelSecretCryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import tools.jackson.databind.json.JsonMapper;

class DatabaseSpringAiRuntimeSecretResolverTest {
    private static final String TEST_ENCRYPTION_KEY = "r".repeat(32);
    private static final String OPTIONS_DIGEST = "a".repeat(64);
    private static final Map<String, Object> OPENAI_OPTIONS = Map.of("deploymentName", "enterprise-chat");

    @Test
    void connectivityProbeCapabilityAndItsImplementationRemainModelHubPackagePrivate() {
        assertThat(Modifier.isPublic(SpringAiConnectivityCredentialResolver.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(DatabaseSpringAiRuntimeSecretResolver.class.getModifiers())).isFalse();

        ProxyFactory proxyFactory = new ProxyFactory(resolver(
                mock(MdlProviderMapper.class),
                mock(MdlConfigMapper.class),
                mock(MdlSecretMapper.class),
                mock(ModelSecretCryptoService.class)
        ));
        proxyFactory.setProxyTargetClass(true);
        assertThat(proxyFactory.getProxy())
                .isInstanceOf(com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolver.class)
                .isInstanceOf(SpringAiConnectivityCredentialResolver.class);
    }

    @Test
    void resolvesOnlyWhenProviderConfigAndSecretMatchTheCompleteRuntimeAuthority() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlConfigMapper configMapper = mock(MdlConfigMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = new ModelSecretCryptoService("kms-test", TEST_ENCRYPTION_KEY);
        MdlSecretPersistenceEntity secret = activeSecret(3001L);
        var material = cryptoService.encrypt("sk-live-secret");
        secret.setSecretCiphertext(material.ciphertext());
        secret.setSecretDigest(material.digest());
        secret.setKmsKeyId(material.kmsKeyId());
        secret.setCipherAlgorithm(material.cipherAlgorithm());
        secret.setCipherVersion(material.cipherVersion());
        when(providerMapper.selectOne(anyProviderQuery())).thenReturn(activeProvider());
        when(configMapper.selectOne(anyConfigQuery())).thenReturn(activePassedConfig());
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(secret);

        String credentialMaterial = resolver(providerMapper, configMapper, secretMapper, cryptoService)
                .resolveCredentialMaterial(credentialRequest());

        assertThat(credentialMaterial).isEqualTo("sk-live-secret");
    }

    @Test
    void resolvesApprovedInactiveConfigForItsFirstConnectivityProbe() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlConfigMapper configMapper = mock(MdlConfigMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = new ModelSecretCryptoService("kms-test", TEST_ENCRYPTION_KEY);
        MdlSecretPersistenceEntity secret = activeSecret(3001L);
        var material = cryptoService.encrypt("sk-first-probe");
        secret.setSecretCiphertext(material.ciphertext());
        secret.setSecretDigest(material.digest());
        secret.setKmsKeyId(material.kmsKeyId());
        secret.setCipherAlgorithm(material.cipherAlgorithm());
        secret.setCipherVersion(material.cipherVersion());
        when(providerMapper.selectOne(anyProviderQuery())).thenReturn(activeProvider());
        when(configMapper.selectOne(anyConfigQuery())).thenReturn(approvedInactiveConfig());
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(secret);

        String credentialMaterial = resolver(providerMapper, configMapper, secretMapper, cryptoService)
                .resolveProbeCredentialMaterial(credentialRequest());

        assertThat(credentialMaterial).isEqualTo("sk-first-probe");
    }

    @Test
    void probeRejectsPendingMissingApprovalIdentityOrImpossibleLingeringEvidenceWithoutDecryptingASecret() {
        MdlConfigPersistenceEntity pending = approvedInactiveConfig();
        pending.setStatus("PENDING_APPROVAL");
        assertProbeRejected(pending);

        MdlConfigPersistenceEntity approvalIdentityMissing = approvedInactiveConfig();
        approvalIdentityMissing.setApprovalRequestId(null);
        assertProbeRejected(approvalIdentityMissing);

        MdlConfigPersistenceEntity impossibleLingeringEvidence = approvedInactiveConfig();
        impossibleLingeringEvidence.setApprovalEvidenceSchema("model-approval-evidence/v1");
        impossibleLingeringEvidence.setApprovalEvidenceDigest("a".repeat(64));
        impossibleLingeringEvidence.setApprovalTargetDigest("b".repeat(64));
        assertProbeRejected(impossibleLingeringEvidence);
    }

    @Test
    void noAuthStillValidatesProviderAndConfigButNeverReadsOrDecryptsASecret() {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlConfigMapper configMapper = mock(MdlConfigMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = mock(ModelSecretCryptoService.class);
        MdlProviderPersistenceEntity provider = activeProvider();
        provider.setProviderCode("ollama-main");
        provider.setBaseUrl("http://ollama:11434");
        provider.setAuthType("no_auth");
        MdlConfigPersistenceEntity config = activePassedConfig();
        config.setModelCode("nomic-embed-text");
        config.setModelType("EMBEDDING");
        config.setOptionsJson("{\"providerKind\":\"ollama\"}");
        when(providerMapper.selectOne(anyProviderQuery())).thenReturn(provider);
        when(configMapper.selectOne(anyConfigQuery())).thenReturn(config);

        String credentialMaterial = resolver(providerMapper, configMapper, secretMapper, cryptoService)
                .resolveCredentialMaterial(noAuthRequest());

        assertThat(credentialMaterial).isNull();
        verifyNoInteractions(secretMapper, cryptoService);
    }

    @Test
    void rejectsSecretOwnedByAnotherProviderWithoutDecryptingIt() {
        assertRejected(
                credentialRequest(),
                activeProvider(),
                activePassedConfig(),
                secret -> secret.setProviderId(3002L)
        );
    }

    @Test
    void rejectsCrossTenantProviderConfigOrSecretWithoutDecryptingIt() {
        MdlProviderPersistenceEntity crossTenantProvider = activeProvider();
        crossTenantProvider.setTenantId(2002L);
        assertRejected(credentialRequest(), crossTenantProvider, activePassedConfig(), ignored -> { });

        MdlConfigPersistenceEntity crossTenantConfig = activePassedConfig();
        crossTenantConfig.setTenantId(2002L);
        assertRejected(credentialRequest(), activeProvider(), crossTenantConfig, ignored -> { });

        assertRejected(
                credentialRequest(),
                activeProvider(),
                activePassedConfig(),
                secret -> secret.setTenantId(2002L)
        );
    }

    @Test
    void rejectsForgedProviderIdentityEndpointAndAuth() {
        assertRejected(
                request("4001", "3002", "openai-main", "openai-compatible",
                        "https://api.openai.example/v1", "api_key", "gpt-4.1-mini", "9001"),
                activeProvider(),
                activePassedConfig(),
                ignored -> { }
        );
        assertRejected(
                request("4001", "3001", "forged-provider", "openai-compatible",
                        "https://api.openai.example/v1", "api_key", "gpt-4.1-mini", "9001"),
                activeProvider(),
                activePassedConfig(),
                ignored -> { }
        );
        assertRejected(
                request("4001", "3001", "openai-main", "openai-compatible",
                        "https://attacker.example/v1", "api_key", "gpt-4.1-mini", "9001"),
                activeProvider(),
                activePassedConfig(),
                ignored -> { }
        );
        assertRejected(
                request("4001", "3001", "openai-main", "openai-compatible",
                        "https://api.openai.example/v1", "gcp_service_account", "gpt-4.1-mini", "9001"),
                activeProvider(),
                activePassedConfig(),
                ignored -> { }
        );
    }

    @Test
    void rejectsForgedConfigIdentityModelAndProviderKind() {
        assertRejected(
                request("4999", "3001", "openai-main", "openai-compatible",
                        "https://api.openai.example/v1", "api_key", "gpt-4.1-mini", "9001"),
                activeProvider(),
                activePassedConfig(),
                ignored -> { }
        );
        assertRejected(
                request("4001", "3001", "openai-main", "openai-compatible",
                        "https://api.openai.example/v1", "api_key", "forged-model", "9001"),
                activeProvider(),
                activePassedConfig(),
                ignored -> { }
        );
        assertRejected(
                request("4001", "3001", "openai-main", "vertex-ai",
                        "https://api.openai.example/v1", "api_key", "gpt-4.1-mini", "9001"),
                activeProvider(),
                activePassedConfig(),
                ignored -> { }
        );
    }

    @Test
    void rejectsStaleConfigVersionModelTypeOptionsOrDigestBeforeSecretLookupAndDecryption() {
        assertAuthorityRejectedBeforeSecret(runtimeAuthorityRequest(
                6, "CHAT", OPENAI_OPTIONS, OPTIONS_DIGEST
        ), activeProvider(), activePassedConfig());
        assertAuthorityRejectedBeforeSecret(runtimeAuthorityRequest(
                7, "EMBEDDING", OPENAI_OPTIONS, OPTIONS_DIGEST
        ), activeProvider(), activePassedConfig());
        assertAuthorityRejectedBeforeSecret(runtimeAuthorityRequest(
                7, "CHAT", Map.of("deploymentName", "stale-chat"), OPTIONS_DIGEST
        ), activeProvider(), activePassedConfig());
        assertAuthorityRejectedBeforeSecret(runtimeAuthorityRequest(
                7, "CHAT", OPENAI_OPTIONS, "b".repeat(64)
        ), activeProvider(), activePassedConfig());
    }

    @Test
    void rejectsAzureDeploymentNameDriftBeforeSecretLookupAndDecryption() {
        MdlProviderPersistenceEntity provider = activeProvider();
        provider.setProviderCode("azure-enterprise");
        provider.setBaseUrl("https://aip-enterprise.openai.azure.com");
        MdlConfigPersistenceEntity config = activePassedConfig();
        config.setModelCode("gpt-4o");
        config.setOptionsJson("""
                {"providerKind":"azure-openai","deploymentName":"chat-prod","apiVersion":"2024-06-01"}
                """);
        SpringAiRuntimeSecretResolutionRequest request = new SpringAiRuntimeSecretResolutionRequest(
                "2001", "4001", "3001", "azure-enterprise", "azure-openai",
                "https://aip-enterprise.openai.azure.com", "api_key", "gpt-4o",
                7, "CHAT",
                Map.of("deploymentName", "chat-stale", "apiVersion", "2024-06-01"),
                OPTIONS_DIGEST, "9001"
        );

        assertAuthorityRejectedBeforeSecret(request, provider, config);
    }

    @Test
    void rejectsVertexProjectIdDriftBeforeSecretLookupAndDecryption() {
        MdlProviderPersistenceEntity provider = activeProvider();
        provider.setProviderCode("vertex-enterprise");
        provider.setBaseUrl("https://us-central1-aiplatform.googleapis.com");
        provider.setAuthType("gcp_service_account");
        MdlConfigPersistenceEntity config = activePassedConfig();
        config.setModelCode("text-embedding-004");
        config.setModelType("EMBEDDING");
        config.setOptionsJson("""
                {"providerKind":"vertex-ai","projectId":"aip-prod","location":"us-central1",\
                "publisher":"google","dimensions":768}
                """);
        SpringAiRuntimeSecretResolutionRequest request = new SpringAiRuntimeSecretResolutionRequest(
                "2001", "4001", "3001", "vertex-enterprise", "vertex-ai",
                "https://us-central1-aiplatform.googleapis.com", "gcp_service_account",
                "text-embedding-004", 7, "EMBEDDING",
                Map.of(
                        "projectId", "aip-stale",
                        "location", "us-central1",
                        "publisher", "google",
                        "dimensions", 768
                ),
                OPTIONS_DIGEST, "9001"
        );

        assertAuthorityRejectedBeforeSecret(request, provider, config);
    }

    @Test
    void rejectsBedrockEndpointRegionDriftBeforeSecretLookupAndDecryption() {
        MdlProviderPersistenceEntity provider = activeProvider();
        provider.setProviderCode("aws-bedrock");
        provider.setBaseUrl("https://bedrock-runtime.us-east-1.amazonaws.com");
        provider.setAuthType("aws_credentials");
        MdlConfigPersistenceEntity config = activePassedConfig();
        config.setModelCode("amazon.titan-embed-text-v2:0");
        config.setModelType("EMBEDDING");
        config.setOptionsJson("{\"providerKind\":\"bedrock\",\"region\":\"us-west-2\",\"timeoutSeconds\":30}");
        Map<String, Object> options = Map.of("region", "us-west-2", "timeoutSeconds", 30);
        SpringAiRuntimeSecretResolutionRequest request = new SpringAiRuntimeSecretResolutionRequest(
                "2001", "4001", "3001", "aws-bedrock", "bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com", "aws_credentials",
                "amazon.titan-embed-text-v2:0", 7, "EMBEDDING", options, OPTIONS_DIGEST, "9001"
        );

        assertAuthorityRejectedBeforeSecret(request, provider, config);
    }

    @Test
    void rejectsDisabledProviderOrConfigAndUnverifiedConnectivity() {
        MdlProviderPersistenceEntity disabledProvider = activeProvider();
        disabledProvider.setStatus("DISABLED");
        assertRejected(credentialRequest(), disabledProvider, activePassedConfig(), ignored -> { });

        MdlConfigPersistenceEntity disabledConfig = activePassedConfig();
        disabledConfig.setStatus("INACTIVE");
        assertRejected(credentialRequest(), activeProvider(), disabledConfig, ignored -> { });

        MdlConfigPersistenceEntity failedConfig = activePassedConfig();
        failedConfig.setConnectivityStatus("FAILED");
        assertRejected(credentialRequest(), activeProvider(), failedConfig, ignored -> { });
    }

    @Test
    void rejectsMissingOrDisabledSecretAndNoAuthCannotBypassConfigAuthority() {
        MdlSecretPersistenceEntity disabledSecret = activeSecret(3001L);
        disabledSecret.setStatus("DISABLED");
        assertRejected(credentialRequest(), activeProvider(), activePassedConfig(), disabledSecret);
        assertRejected(credentialRequest(), activeProvider(), activePassedConfig(), (MdlSecretPersistenceEntity) null);

        MdlProviderPersistenceEntity noAuthProvider = activeProvider();
        noAuthProvider.setProviderCode("ollama-main");
        noAuthProvider.setBaseUrl("http://ollama:11434");
        noAuthProvider.setAuthType("no_auth");
        MdlConfigPersistenceEntity noAuthConfig = activePassedConfig();
        noAuthConfig.setModelCode("nomic-embed-text");
        noAuthConfig.setModelType("EMBEDDING");
        noAuthConfig.setOptionsJson("{\"providerKind\":\"openai-compatible\"}");
        assertRejected(noAuthRequest(), noAuthProvider, noAuthConfig, (MdlSecretPersistenceEntity) null);
    }

    private static void assertRejected(
            SpringAiRuntimeSecretResolutionRequest request,
            MdlProviderPersistenceEntity provider,
            MdlConfigPersistenceEntity config,
            Consumer<MdlSecretPersistenceEntity> secretMutation
    ) {
        MdlSecretPersistenceEntity secret = activeSecret(3001L);
        secretMutation.accept(secret);
        assertRejected(request, provider, config, secret);
    }

    private static void assertProbeRejected(MdlConfigPersistenceEntity config) {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlConfigMapper configMapper = mock(MdlConfigMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = mock(ModelSecretCryptoService.class);
        when(providerMapper.selectOne(anyProviderQuery())).thenReturn(activeProvider());
        when(configMapper.selectOne(anyConfigQuery())).thenReturn(config);
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(activeSecret(3001L));

        assertThatThrownBy(() -> resolver(providerMapper, configMapper, secretMapper, cryptoService)
                .resolveProbeCredentialMaterial(credentialRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("probe authority 校验失败");
        verifyNoInteractions(cryptoService);
    }

    private static void assertAuthorityRejectedBeforeSecret(
            SpringAiRuntimeSecretResolutionRequest request,
            MdlProviderPersistenceEntity provider,
            MdlConfigPersistenceEntity config
    ) {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlConfigMapper configMapper = mock(MdlConfigMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = mock(ModelSecretCryptoService.class);
        when(providerMapper.selectOne(anyProviderQuery())).thenReturn(provider);
        when(configMapper.selectOne(anyConfigQuery())).thenReturn(config);

        assertThatThrownBy(() -> resolver(providerMapper, configMapper, secretMapper, cryptoService)
                .resolveCredentialMaterial(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("运行时 authority 校验失败");
        verifyNoInteractions(secretMapper, cryptoService);
    }

    private static void assertRejected(
            SpringAiRuntimeSecretResolutionRequest request,
            MdlProviderPersistenceEntity provider,
            MdlConfigPersistenceEntity config,
            MdlSecretPersistenceEntity secret
    ) {
        MdlProviderMapper providerMapper = mock(MdlProviderMapper.class);
        MdlConfigMapper configMapper = mock(MdlConfigMapper.class);
        MdlSecretMapper secretMapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = mock(ModelSecretCryptoService.class);
        when(providerMapper.selectOne(anyProviderQuery())).thenReturn(provider);
        when(configMapper.selectOne(anyConfigQuery())).thenReturn(config);
        when(secretMapper.selectOne(anySecretQuery())).thenReturn(secret);

        assertThatThrownBy(() -> resolver(providerMapper, configMapper, secretMapper, cryptoService)
                .resolveCredentialMaterial(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("运行时 authority 校验失败");
        verifyNoInteractions(cryptoService);
    }

    private static DatabaseSpringAiRuntimeSecretResolver resolver(
            MdlProviderMapper providerMapper,
            MdlConfigMapper configMapper,
            MdlSecretMapper secretMapper,
            ModelSecretCryptoService cryptoService
    ) {
        ModelRuntimeAuthorityDigestMapper digestMapper = mock(ModelRuntimeAuthorityDigestMapper.class);
        when(digestMapper.canonicalEffectiveOptionsDigest(anyString())).thenReturn(OPTIONS_DIGEST);
        return new DatabaseSpringAiRuntimeSecretResolver(
                providerMapper,
                configMapper,
                secretMapper,
                cryptoService,
                new JsonMapper(),
                new ModelRuntimeAuthorityDigest(digestMapper, new JsonMapper())
        );
    }

    private static SpringAiRuntimeSecretResolutionRequest credentialRequest() {
        return request(
                "4001",
                "3001",
                "openai-main",
                "openai-compatible",
                "https://api.openai.example/v1",
                "api_key",
                "gpt-4.1-mini",
                "9001"
        );
    }

    private static SpringAiRuntimeSecretResolutionRequest noAuthRequest() {
        return request(
                "4001",
                "3001",
                "ollama-main",
                "ollama",
                "http://ollama:11434",
                "no_auth",
                "nomic-embed-text",
                null
        );
    }

    private static SpringAiRuntimeSecretResolutionRequest request(
            String modelConfigId,
            String providerId,
            String providerCode,
            String providerKind,
            String endpoint,
            String authType,
            String modelCode,
            String secretRefId
    ) {
        Map<String, Object> effectiveOptions = "ollama".equals(providerKind) ? Map.of() : OPENAI_OPTIONS;
        String modelType = "ollama".equals(providerKind) ? "EMBEDDING" : "CHAT";
        return new SpringAiRuntimeSecretResolutionRequest(
                "2001",
                modelConfigId,
                providerId,
                providerCode,
                providerKind,
                endpoint,
                authType,
                modelCode,
                7,
                modelType,
                effectiveOptions,
                OPTIONS_DIGEST,
                secretRefId
        );
    }

    private static SpringAiRuntimeSecretResolutionRequest runtimeAuthorityRequest(
            int modelConfigVersion,
            String modelType,
            Map<String, Object> effectiveOptions,
            String effectiveOptionsDigest
    ) {
        return new SpringAiRuntimeSecretResolutionRequest(
                "2001", "4001", "3001", "openai-main", "openai-compatible",
                "https://api.openai.example/v1", "api_key", "gpt-4.1-mini",
                modelConfigVersion, modelType, effectiveOptions, effectiveOptionsDigest, "9001"
        );
    }

    private static MdlProviderPersistenceEntity activeProvider() {
        MdlProviderPersistenceEntity provider = new MdlProviderPersistenceEntity();
        provider.setId(3001L);
        provider.setTenantId(2001L);
        provider.setProviderCode("openai-main");
        provider.setBaseUrl("https://api.openai.example/v1");
        provider.setAuthType("api_key");
        provider.setStatus("ACTIVE");
        return provider;
    }

    private static MdlConfigPersistenceEntity activePassedConfig() {
        MdlConfigPersistenceEntity config = new MdlConfigPersistenceEntity();
        config.setId(4001L);
        config.setTenantId(2001L);
        config.setProviderId(3001L);
        config.setModelCode("gpt-4.1-mini");
        config.setModelType("CHAT");
        config.setVersion(7);
        config.setOptionsJson("{\"providerKind\":\"openai-compatible\",\"deploymentName\":\"enterprise-chat\"}");
        config.setConnectivityStatus("PASSED");
        config.setStatus("ACTIVE");
        return config;
    }

    private static MdlConfigPersistenceEntity approvedInactiveConfig() {
        MdlConfigPersistenceEntity config = activePassedConfig();
        config.setStatus("INACTIVE");
        config.setConnectivityStatus("NOT_TESTED");
        config.setApprovalRequestId("approval-4001");
        return config;
    }

    private static MdlSecretPersistenceEntity activeSecret(long providerId) {
        MdlSecretPersistenceEntity secret = new MdlSecretPersistenceEntity();
        secret.setId(9001L);
        secret.setTenantId(2001L);
        secret.setProviderId(providerId);
        secret.setStatus("ACTIVE");
        return secret;
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<MdlProviderPersistenceEntity> anyProviderQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<MdlConfigPersistenceEntity> anyConfigQuery() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<MdlSecretPersistenceEntity> anySecretQuery() {
        return any(LambdaQueryWrapper.class);
    }
}
