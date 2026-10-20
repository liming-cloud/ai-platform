package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderProbeObservation;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformModelDeploymentProbeRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentProbe;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderCredentialResolver;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import java.util.Objects;

/**
 * 平台范围的最小 Chat／Embedding 协议探测，凭据只来自平台专用解析器。
 * 不产生完整 S2 验证、发布或授权事实；重排必须使用独立协议。
 */
public final class OpenAiCompatiblePlatformModelDeploymentProbe implements PlatformModelDeploymentProbe {
    private final OpenAiCompatibleConnectionProbe protocol;

    /** 只接受平台凭据拥有者，不接受租户解析器或租户标识。 */
    public OpenAiCompatiblePlatformModelDeploymentProbe(PlatformProviderCredentialResolver secrets,
            OutboundEndpointPolicy endpointPolicy) {
        Objects.requireNonNull(secrets, "平台凭据解析器不能为空");
        protocol = new OpenAiCompatibleConnectionProbe(reference -> secrets.resolve(reference.value()), endpointPolicy);
    }

    /** 只声明运行协议，不按域名或模型名称推断厂商能力。 */
    @Override
    public ProviderKind providerKind() { return ProviderKind.OPENAI_COMPATIBLE; }

    /** 执行一次有界最小调用，失败只返回安全分类，不返回正文或原始响应。 */
    @Override
    public ProviderProbeObservation probe(PlatformModelDeploymentProbeRequest request) {
        return protocol.probePlatform(request);
    }
}
