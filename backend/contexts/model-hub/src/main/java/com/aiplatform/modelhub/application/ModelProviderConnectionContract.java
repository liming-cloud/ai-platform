package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.domain.ModelProviderAuthTypes;
import com.aiplatform.modelhub.domain.ModelProviderKinds;
import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import java.util.Locale;
import java.util.Objects;

/**
 * 模型供应商创建入口的连接能力契约。类型、鉴权和地址必须在任何 ID 分配、密钥写入或审批副作用前通过；
 * 本地确定性运行时没有网络连接，只接受固定内部标识，其余受支持供应商继续经过企业出站地址策略。
 */
public final class ModelProviderConnectionContract {
    public static final String LOCAL_DETERMINISTIC_PROVIDER = ModelProviderKinds.LOCAL_DETERMINISTIC;
    public static final String LOCAL_DETERMINISTIC_ENDPOINT = "local://deterministic";

    private final OutboundEndpointPolicy outboundEndpointPolicy;
    private final boolean localDeterministicEnabled;

    public ModelProviderConnectionContract(
            OutboundEndpointPolicy outboundEndpointPolicy,
            boolean localDeterministicEnabled
    ) {
        this.outboundEndpointPolicy = Objects.requireNonNull(
                outboundEndpointPolicy,
                "模型出站地址策略不能为空"
        );
        this.localDeterministicEnabled = localDeterministicEnabled;
    }

    public String validateAndNormalize(String providerType, String endpoint, String authType) {
        String normalizedProviderType = requireText(providerType, "模型供应商类型")
                .toLowerCase(Locale.ROOT);
        String canonicalProviderType = ModelProviderKinds.canonicalize(normalizedProviderType);
        String requiredAuthType = ModelProviderKinds.requiredAuthType(normalizedProviderType);
        String normalizedAuthType = ModelProviderAuthTypes.normalize(authType);
        if (!requiredAuthType.equals(normalizedAuthType)) {
            throw new IllegalArgumentException(
                    "模型供应商 " + normalizedProviderType + " 仅支持 " + requiredAuthType + " 鉴权"
            );
        }
        if (LOCAL_DETERMINISTIC_PROVIDER.equals(canonicalProviderType) && !localDeterministicEnabled) {
            throw new IllegalArgumentException("当前部署未启用本地确定性运行时");
        }
        if (!LOCAL_DETERMINISTIC_PROVIDER.equals(canonicalProviderType)) {
            return outboundEndpointPolicy.validateAndNormalize(endpoint).toString();
        }

        String normalizedEndpoint = requireText(endpoint, "模型供应商访问地址");
        if (!LOCAL_DETERMINISTIC_ENDPOINT.equals(normalizedEndpoint)) {
            throw new IllegalArgumentException(
                    "本地确定性运行时地址必须是 " + LOCAL_DETERMINISTIC_ENDPOINT
            );
        }
        return LOCAL_DETERMINISTIC_ENDPOINT;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
