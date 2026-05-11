package org.fujay.ai.platform.ai;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiGatewayClientFactory {

    private final Map<AiProviderType, AiGatewayClient> clients = new EnumMap<>(AiProviderType.class);

    public AiGatewayClientFactory(List<AiGatewayClient> gatewayClients) {
        for (AiGatewayClient gatewayClient : gatewayClients) {
            for (AiProviderType providerType : gatewayClient.supportedProviderTypes()) {
                if (clients.putIfAbsent(providerType, gatewayClient) != null) {
                    throw new IllegalStateException("模型供应商类型存在重复网关实现：" + providerType);
                }
            }
        }
    }

    public AiGatewayClient requiredClient(AiProviderType providerType) {
        AiGatewayClient client = clients.get(providerType);
        if (client == null) {
            throw new IllegalArgumentException("不支持的模型供应商类型：" + providerType);
        }
        return client;
    }
}
