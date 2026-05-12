package org.fujay.ai.platform.ai;

import org.springframework.stereotype.Component;

@Component
public class SpringAiGatewayFacade implements AiGatewayFacade {

    private final AiGatewayClientFactory clientFactory;

    public SpringAiGatewayFacade(AiGatewayClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public ChatTestResult testChat(ChatTestCommand command) {
        return clientFactory.requiredClient(command.providerType()).testChat(command);
    }

    @Override
    public ChatInvocationResult invokeChat(ChatInvocationCommand command) {
        return clientFactory.requiredClient(command.providerType()).invokeChat(command);
    }
}
