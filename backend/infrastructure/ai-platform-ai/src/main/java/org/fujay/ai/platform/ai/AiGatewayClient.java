package org.fujay.ai.platform.ai;

import java.util.Set;

public interface AiGatewayClient {

    Set<AiProviderType> supportedProviderTypes();

    ChatTestResult testChat(ChatTestCommand command);

    ChatInvocationResult invokeChat(ChatInvocationCommand command);
}
