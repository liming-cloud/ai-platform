package org.fujay.ai.platform.model.infrastructure.ai;

import org.fujay.ai.platform.ai.AiGatewayFacade;
import org.fujay.ai.platform.ai.AiProviderType;
import org.fujay.ai.platform.ai.ChatInvocationCommand;
import org.fujay.ai.platform.ai.ChatInvocationResult;
import org.fujay.ai.platform.ai.ChatTestCommand;
import org.fujay.ai.platform.ai.ChatTestResult;
import org.fujay.ai.platform.model.application.port.ModelGatewayChatRequest;
import org.fujay.ai.platform.model.application.port.ModelGatewayChatResult;
import org.fujay.ai.platform.model.application.port.ModelGatewayPort;
import org.fujay.ai.platform.security.SecretCipher;
import org.springframework.stereotype.Component;

@Component
public class AiGatewayModelGatewayAdapter implements ModelGatewayPort {

    private final AiGatewayFacade aiGatewayFacade;
    private final SecretCipher secretCipher;

    public AiGatewayModelGatewayAdapter(AiGatewayFacade aiGatewayFacade, SecretCipher secretCipher) {
        this.aiGatewayFacade = aiGatewayFacade;
        this.secretCipher = secretCipher;
    }

    @Override
    public ModelGatewayChatResult testChat(ModelGatewayChatRequest request) {
        ChatTestResult result = aiGatewayFacade.testChat(new ChatTestCommand(
                toAiProviderType(request),
                request.baseUrl(),
                secretCipher.decrypt(request.apiKeyCiphertext()),
                request.modelCode(),
                request.prompt(),
                request.maxOutputTokens(),
                request.temperature(),
                request.topP(),
                request.frequencyPenalty(),
                request.presencePenalty(),
                request.reasoningEffort()));
        return new ModelGatewayChatResult(
                result.message(), result.promptTokens(), result.completionTokens(), result.totalTokens());
    }

    @Override
    public ModelGatewayChatResult invokeChat(ModelGatewayChatRequest request) {
        ChatInvocationResult result = aiGatewayFacade.invokeChat(new ChatInvocationCommand(
                toAiProviderType(request),
                request.baseUrl(),
                secretCipher.decrypt(request.apiKeyCiphertext()),
                request.modelCode(),
                request.prompt(),
                request.maxOutputTokens(),
                request.temperature(),
                request.topP(),
                request.frequencyPenalty(),
                request.presencePenalty(),
                request.reasoningEffort()));
        return new ModelGatewayChatResult(
                result.output(), result.promptTokens(), result.completionTokens(), result.totalTokens());
    }

    private AiProviderType toAiProviderType(ModelGatewayChatRequest request) {
        return AiProviderType.valueOf(request.providerType().name());
    }
}
