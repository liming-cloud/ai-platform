package com.aiplatform.modelhub.infrastructure.springai.adapter;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;
import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolver;
import com.aiplatform.infra.springai.model.provider.bedrock.support.BedrockProviderSupport;
import com.aiplatform.modelhub.domain.ModelRuntimeOptions;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlConfigPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlProviderPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlConfigMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlProviderMapper;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import com.aiplatform.modelhub.infrastructure.secret.ModelSecretCryptoService;
import com.aiplatform.shared.kernel.Sha256Digests;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring AI 运行时凭证材料解析器，按租户和凭证引用从 model-hub 密钥库解密。
 */
@Component
class DatabaseSpringAiRuntimeSecretResolver
        implements SpringAiRuntimeSecretResolver, SpringAiConnectivityCredentialResolver {
    private final MdlProviderMapper providerMapper;
    private final MdlConfigMapper configMapper;
    private final MdlSecretMapper secretMapper;
    private final ModelSecretCryptoService cryptoService;
    private final JsonMapper objectMapper;
    private final ModelRuntimeAuthorityDigest authorityDigest;

    DatabaseSpringAiRuntimeSecretResolver(
            MdlProviderMapper providerMapper,
            MdlConfigMapper configMapper,
            MdlSecretMapper secretMapper,
            ModelSecretCryptoService cryptoService,
            JsonMapper objectMapper,
            ModelRuntimeAuthorityDigest authorityDigest
    ) {
        this.providerMapper = Objects.requireNonNull(providerMapper, "模型供应商 Mapper 不能为空");
        this.configMapper = Objects.requireNonNull(configMapper, "模型配置 Mapper 不能为空");
        this.secretMapper = Objects.requireNonNull(secretMapper, "模型供应商凭证材料 Mapper 不能为空");
        this.cryptoService = Objects.requireNonNull(cryptoService, "模型供应商凭证材料加解密服务不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
        this.authorityDigest = Objects.requireNonNull(
                authorityDigest,
                "模型运行时 authority 摘要服务不能为空"
        );
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String resolveCredentialMaterial(SpringAiRuntimeSecretResolutionRequest request) {
        return resolveCredentialMaterial(request, RuntimeAuthorityMode.RUNTIME);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String resolveProbeCredentialMaterial(SpringAiRuntimeSecretResolutionRequest request) {
        return resolveCredentialMaterial(request, RuntimeAuthorityMode.CONNECTIVITY_PROBE);
    }

    private String resolveCredentialMaterial(
            SpringAiRuntimeSecretResolutionRequest request,
            RuntimeAuthorityMode mode
    ) {
        Objects.requireNonNull(request, "Spring AI 运行时凭证解析请求不能为空");
        long tenantId = parseRequiredLong(request.tenantId(), "租户 ID");
        long providerId = parseRequiredLong(request.providerId(), "模型供应商 ID");
        long modelConfigId = parseRequiredLong(request.modelConfigId(), "模型配置 ID");

        MdlProviderPersistenceEntity provider = providerMapper.selectOne(
                new LambdaQueryWrapper<MdlProviderPersistenceEntity>()
                        .eq(MdlProviderPersistenceEntity::getTenantId, tenantId)
                        .eq(MdlProviderPersistenceEntity::getId, providerId)
                        .eq(MdlProviderPersistenceEntity::getProviderCode, request.providerCode())
                        .eq(MdlProviderPersistenceEntity::getBaseUrl, request.endpoint())
                        .eq(MdlProviderPersistenceEntity::getAuthType, request.authType())
                        .eq(MdlProviderPersistenceEntity::getStatus, "ACTIVE")
                        .isNull(MdlProviderPersistenceEntity::getDeletedAt)
                        .last("limit 1")
        );
        requireMatchingProvider(provider, request, tenantId, providerId, mode);

        LambdaQueryWrapper<MdlConfigPersistenceEntity> configQuery =
                new LambdaQueryWrapper<MdlConfigPersistenceEntity>()
                        .eq(MdlConfigPersistenceEntity::getTenantId, tenantId)
                        .eq(MdlConfigPersistenceEntity::getId, modelConfigId)
                        .eq(MdlConfigPersistenceEntity::getProviderId, providerId)
                        .eq(MdlConfigPersistenceEntity::getModelCode, request.modelCode())
                        .isNull(MdlConfigPersistenceEntity::getDeletedAt);
        if (mode == RuntimeAuthorityMode.RUNTIME) {
            configQuery.eq(MdlConfigPersistenceEntity::getStatus, "ACTIVE")
                    .eq(MdlConfigPersistenceEntity::getConnectivityStatus, "PASSED");
        }
        MdlConfigPersistenceEntity config = configMapper.selectOne(configQuery.last("limit 1"));
        requireMatchingConfig(config, request, tenantId, providerId, modelConfigId, mode);
        requireProviderEgressBinding(request, mode);

        if (!request.requiresCredentialMaterial()) {
            return null;
        }

        long secretRefId = parseRequiredLong(request.secretRefId(), "模型供应商凭证材料引用 ID");
        MdlSecretPersistenceEntity row = secretMapper.selectOne(new LambdaQueryWrapper<MdlSecretPersistenceEntity>()
                .eq(MdlSecretPersistenceEntity::getTenantId, tenantId)
                .eq(MdlSecretPersistenceEntity::getId, secretRefId)
                .eq(MdlSecretPersistenceEntity::getProviderId, providerId)
                .eq(MdlSecretPersistenceEntity::getStatus, "ACTIVE")
                .isNull(MdlSecretPersistenceEntity::getDeletedAt)
                .last("limit 1"));
        requireMatchingSecret(row, tenantId, providerId, secretRefId, mode);
        return cryptoService.decrypt(
                row.getSecretCiphertext(),
                row.getSecretDigest(),
                row.getKmsKeyId(),
                row.getCipherAlgorithm(),
                row.getCipherVersion()
        );
    }

    private static void requireMatchingProvider(
            MdlProviderPersistenceEntity provider,
            SpringAiRuntimeSecretResolutionRequest request,
            long tenantId,
            long providerId,
            RuntimeAuthorityMode mode
    ) {
        if (provider == null
                || !Objects.equals(provider.getTenantId(), tenantId)
                || !Objects.equals(provider.getId(), providerId)
                || !Objects.equals(provider.getProviderCode(), request.providerCode())
                || !Objects.equals(provider.getBaseUrl(), request.endpoint())
                || !Objects.equals(provider.getAuthType(), request.authType())
                || !"ACTIVE".equals(provider.getStatus())
                || provider.getDeletedAt() != null) {
            throw authorityFailure(mode);
        }
    }

    private void requireMatchingConfig(
            MdlConfigPersistenceEntity config,
            SpringAiRuntimeSecretResolutionRequest request,
            long tenantId,
            long providerId,
            long modelConfigId,
            RuntimeAuthorityMode mode
    ) {
        RuntimeOptionsAuthority runtimeAuthority = readRuntimeOptionsAuthority(config, mode);
        if (config == null
                || !Objects.equals(config.getTenantId(), tenantId)
                || !Objects.equals(config.getId(), modelConfigId)
                || !Objects.equals(config.getProviderId(), providerId)
                || !Objects.equals(config.getModelCode(), request.modelCode())
                || !Objects.equals(config.getVersion(), request.modelConfigVersion())
                || !Objects.equals(config.getModelType(), request.modelType())
                || config.getDeletedAt() != null
                || !Objects.equals(runtimeAuthority.providerKind(), request.providerKind())
                || !Objects.equals(runtimeAuthority.effectiveOptions(), request.effectiveOptions())
                || !Sha256Digests.constantTimeEquals(
                        runtimeAuthority.effectiveOptionsDigest(),
                        request.effectiveOptionsDigest()
                )
                || !validConfigState(config, mode)) {
            throw authorityFailure(mode);
        }
    }

    private static void requireMatchingSecret(
            MdlSecretPersistenceEntity secret,
            long tenantId,
            long providerId,
            long secretRefId,
            RuntimeAuthorityMode mode
    ) {
        if (secret == null
                || !Objects.equals(secret.getTenantId(), tenantId)
                || !Objects.equals(secret.getId(), secretRefId)
                || !Objects.equals(secret.getProviderId(), providerId)
                || !"ACTIVE".equals(secret.getStatus())
                || secret.getDeletedAt() != null) {
            throw authorityFailure(mode);
        }
    }

    private RuntimeOptionsAuthority readRuntimeOptionsAuthority(
            MdlConfigPersistenceEntity config,
            RuntimeAuthorityMode mode
    ) {
        if (config == null || config.getOptionsJson() == null || config.getOptionsJson().isBlank()) {
            throw authorityFailure(mode);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = objectMapper.readValue(config.getOptionsJson(), Map.class);
            ModelRuntimeOptions runtimeOptions = new ModelRuntimeOptions(values);
            Map<String, Object> effectiveOptions = runtimeOptions.springAiOptions();
            return new RuntimeOptionsAuthority(
                    runtimeOptions.providerKind(),
                    effectiveOptions,
                    authorityDigest.digest(effectiveOptions)
            );
        } catch (RuntimeException exception) {
            throw authorityFailure(mode);
        }
    }

    private static void requireProviderEgressBinding(
            SpringAiRuntimeSecretResolutionRequest request,
            RuntimeAuthorityMode mode
    ) {
        try {
            BedrockProviderSupport.requireCanonicalEndpointForRegion(
                    request.providerKind(),
                    request.endpoint(),
                    request.effectiveOptions()
            );
        } catch (IllegalArgumentException exception) {
            throw authorityFailure(mode);
        }
    }

    private static boolean validConfigState(MdlConfigPersistenceEntity config, RuntimeAuthorityMode mode) {
        if (mode == RuntimeAuthorityMode.RUNTIME) {
            return "ACTIVE".equals(config.getStatus())
                    && "PASSED".equals(config.getConnectivityStatus());
        }
        return ("INACTIVE".equals(config.getStatus()) || "ACTIVE".equals(config.getStatus()))
                && hasText(config.getApprovalRequestId())
                && config.getApprovalEvidenceSchema() == null
                && config.getApprovalEvidenceDigest() == null
                && config.getApprovalTargetDigest() == null
                && validConnectivityStatus(config.getConnectivityStatus());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validConnectivityStatus(String value) {
        return "NOT_TESTED".equals(value) || "PASSED".equals(value) || "FAILED".equals(value);
    }

    private static IllegalStateException authorityFailure(RuntimeAuthorityMode mode) {
        return new IllegalStateException(mode == RuntimeAuthorityMode.RUNTIME
                ? "模型运行时 authority 校验失败"
                : "模型连通性 probe authority 校验失败");
    }
}
