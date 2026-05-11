package org.fujay.ai.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiGatewayFactoryTest {

    @Test
    void facadeDelegatesToClientSelectedByProviderType() {
        StubGatewayClient openAiClient = new StubGatewayClient(Set.of(AiProviderType.OPENAI), "openai");
        StubGatewayClient compatibleClient =
                new StubGatewayClient(Set.of(AiProviderType.OPENAI_COMPATIBLE), "compatible");
        AiGatewayFacade facade =
                new SpringAiGatewayFacade(new AiGatewayClientFactory(List.of(openAiClient, compatibleClient)));

        ChatTestResult result = facade.testChat(new ChatTestCommand(
                AiProviderType.OPENAI_COMPATIBLE,
                "https://models.example.com",
                "sk-test",
                "qwen-compatible",
                "ping",
                128,
                null,
                null,
                null,
                null,
                null));

        assertThat(result.message()).isEqualTo("compatible");
        assertThat(openAiClient.callCount).isZero();
        assertThat(compatibleClient.callCount).isEqualTo(1);
    }

    @Test
    void factoryRejectsProviderTypeWithoutClientImplementation() {
        AiGatewayClientFactory factory =
                new AiGatewayClientFactory(List.of(new StubGatewayClient(Set.of(AiProviderType.OPENAI), "openai")));

        assertThatThrownBy(() -> factory.requiredClient(AiProviderType.NATIVE_VENDOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的模型供应商类型：NATIVE_VENDOR");
    }

    private static final class StubGatewayClient implements AiGatewayClient {
        private final Set<AiProviderType> providerTypes;
        private final String message;
        private int callCount;

        private StubGatewayClient(Set<AiProviderType> providerTypes, String message) {
            this.providerTypes = providerTypes;
            this.message = message;
        }

        @Override
        public Set<AiProviderType> supportedProviderTypes() {
            return providerTypes;
        }

        @Override
        public ChatTestResult testChat(ChatTestCommand command) {
            callCount++;
            return new ChatTestResult(message, null, null, null);
        }
    }
}
