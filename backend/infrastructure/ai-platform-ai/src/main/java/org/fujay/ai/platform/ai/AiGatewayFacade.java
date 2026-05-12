package org.fujay.ai.platform.ai;

public interface AiGatewayFacade {

    ChatTestResult testChat(ChatTestCommand command);

    ChatInvocationResult invokeChat(ChatInvocationCommand command);
}
