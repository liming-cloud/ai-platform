package com.aiplatform.foundation.modelruntime.application.extension.service;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelCapability;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderModelDiscoveryObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformProviderModelDiscoveryObservation;
import java.util.Set;
import java.util.stream.Collectors;

/** 在模型上下文内映射领域事实与供应商扩展契约。 */
public final class ExtensionContractMapper {
    private ExtensionContractMapper() {
    }

    /** 将领域能力集合转换为扩展可见枚举。 */
    public static Set<ModelCapability> capabilities(
            Set<com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability> values) {
        return values.stream().map(value -> ModelCapability.valueOf(value.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 在拥有者上下文内恢复领域能力集合。 */
    public static Set<com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability> domainCapabilities(
            Set<ModelCapability> values) {
        return values.stream()
                .map(value -> com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability.valueOf(value.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 将租户探测请求转换为供应商扩展请求。 */
    public static com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderProbeRequest probe(
            com.aiplatform.foundation.modelruntime.application.probe.dto.ProviderProbeRequest request) {
        return new com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderProbeRequest(
                request.tenantId(), ProviderKind.valueOf(request.providerKind().name()), request.endpoint(),
                request.credentialReference().value(), request.modelCode(),
                capabilities(request.requestedCapabilities()), request.timeout());
    }

    /** 将平台探测请求转换为供应商扩展请求。 */
    public static com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderProbeRequest platformProbe(
            com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderProbeRequest request) {
        return new com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderProbeRequest(
                ProviderKind.valueOf(request.providerKind().name()), request.endpoint(),
                request.credentialReference().value(), request.modelCode(), request.timeout());
    }

    /** 将租户模型发现请求转换为供应商扩展请求。 */
    public static com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderModelDiscoveryRequest discovery(
            com.aiplatform.foundation.modelruntime.application.provider.dto.ProviderModelDiscoveryRequest request) {
        return new com.aiplatform.foundation.modelruntime.application.extension.facade.request.ProviderModelDiscoveryRequest(
                request.tenantId(), ProviderKind.valueOf(request.providerKind().name()), request.endpoint(),
                request.credentialReference().value(), request.timeout());
    }

    /** 将平台模型发现请求转换为供应商扩展请求。 */
    public static com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest platformDiscovery(
            com.aiplatform.foundation.modelruntime.application.platformsupply.dto.PlatformProviderModelDiscoveryRequest request) {
        return new com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderModelDiscoveryRequest(
                ProviderKind.valueOf(request.providerKind().name()),
                com.aiplatform.foundation.modelruntime.application.extension.facade.PlatformModelDiscoveryProfile
                        .valueOf(request.discoveryProfile().name()), request.endpoint(), request.region(),
                request.credentialReference().value(),
                request.discoveryCredentials().map(value ->
                        new com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformProviderDiscoveryCredentialReferences(
                                value.accessKeyId().value(), value.secretAccessKey().value())),
                request.timeout());
    }

    /** 校验扩展观测值后在模型上下文内恢复领域事实。 */
    public static com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation domainDiscovery(
            ProviderModelDiscoveryObservation observation) {
        return new com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscoveryObservation(
                observation.succeeded(),
                observation.models().stream()
                        .map(model -> new com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel(
                                model.modelCode(),
                                com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.valueOf(
                                        model.modelType().name()),
                                domainCapabilities(model.capabilities())))
                        .collect(Collectors.toUnmodifiableSet()),
                observation.resultCode(), observation.elapsed(), observation.discoveredAt());
    }

    /** 校验平台扩展观测值后在模型上下文内恢复领域事实。 */
    public static com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformProviderModelDiscoveryObservation domainPlatformDiscovery(
            PlatformProviderModelDiscoveryObservation observation) {
        return new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformProviderModelDiscoveryObservation(
                observation.succeeded(),
                observation.candidates().stream()
                        .map(candidate -> new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate(
                                candidate.modelCode(),
                                candidate.modelType() == null ? null
                                        : com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType.valueOf(
                                                candidate.modelType().name()),
                                domainCapabilities(candidate.capabilities()),
                                com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification.valueOf(
                                        candidate.classification().name()), candidate.displayName(), candidate.source() == null ? null
                        : new com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateSource(
                                candidate.source().profile(), candidate.source().listSource(), candidate.source().mappingVersion(), candidate.source().mappingDigest())))
                        .collect(Collectors.toUnmodifiableSet()),
                observation.resultCode(), observation.elapsed(), observation.discoveredAt());
    }
}
