package com.aiplatform.infra.springai.model.provider.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;

class LocalDeterministicSpringAiModelProbeClientTest {
    @Test
    void probesLocalDeterministicEmbeddingRuntimeThroughSpringAiModel() {
        LocalDeterministicEmbeddingModelFactory factory = new LocalDeterministicEmbeddingModelFactory();
        LocalDeterministicSpringAiModelProbeClient client = newClient(factory, new LocalDeterministicChatModelFactory());
        SpringAiModelRuntimeRequest request = localEmbeddingRequest();
        var options = EmbeddingOptions.builder()
                .model("local-bge-m3")
                .dimensions(64)
                .build();

        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(request, options));

        assertThat(client.supports(new SpringAiModelRuntimeProbe(request, options))).isTrue();
        assertThat(result.connected()).isTrue();
        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.message()).contains("Spring AI EmbeddingModel");
    }

    @Test
    void producesDeterministicVectorsWithRequestedDimension() {
        LocalDeterministicEmbeddingModelFactory factory = new LocalDeterministicEmbeddingModelFactory();
        SpringAiModelRuntimeRequest request = localEmbeddingRequest();
        var options = EmbeddingOptions.builder()
                .model("local-bge-m3")
                .dimensions(64)
                .build();

        var first = factory.create(request, options).call(new EmbeddingRequest(List.of("企业制度"), options)).getResult().getOutput();
        var second = factory.create(request, options).call(new EmbeddingRequest(List.of("企业制度"), options)).getResult().getOutput();

        assertThat(first).hasSize(64);
        assertThat(second).containsExactly(first);
    }

    @Test
    void acceptsEmbeddingDimensionAtInclusiveEnterpriseBounds() {
        LocalDeterministicEmbeddingModelFactory factory = new LocalDeterministicEmbeddingModelFactory();
        EmbeddingOptions lowerOptions = EmbeddingOptions.builder()
                .model("local-bge-m3")
                .dimensions(1)
                .build();

        assertThat(factory.create(
                localEmbeddingRequest(1, Map.of("dimensions", 1)),
                lowerOptions
        ).call(new EmbeddingRequest(List.of("下限"), lowerOptions)).getResult().getOutput()).hasSize(1);

        int upper = LocalDeterministicRuntimeContract.MAX_EMBEDDING_DIMENSION;
        EmbeddingOptions upperOptions = EmbeddingOptions.builder()
                .model("local-bge-m3")
                .dimensions(upper)
                .build();
        assertThat(factory.create(
                localEmbeddingRequest(upper, Map.of("dimensions", upper)),
                upperOptions
        ).call(new EmbeddingRequest(List.of("上限"), upperOptions)).getResult().getOutput()).hasSize(upper);
    }

    @Test
    void rejectsDimensionMismatchAndUpperPlusOneBeforeEmbeddingAllocation() {
        LocalDeterministicEmbeddingModelFactory factory = new LocalDeterministicEmbeddingModelFactory();
        EmbeddingOptions mismatchedOptions = EmbeddingOptions.builder()
                .model("local-bge-m3")
                .dimensions(32)
                .build();

        assertThatThrownBy(() -> factory.create(localEmbeddingRequest(), mismatchedOptions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimensions 必须与 embeddingDimension 一致");

        int unsafeDimension = LocalDeterministicRuntimeContract.MAX_EMBEDDING_DIMENSION + 1;
        assertThatThrownBy(() -> new LocalDeterministicEmbeddingModel(unsafeDimension))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得大于")
                .hasMessageContaining(String.valueOf(LocalDeterministicRuntimeContract.MAX_EMBEDDING_DIMENSION));
    }

    @Test
    void rejectsCredentialAuthForLocalRuntime() {
        LocalDeterministicSpringAiModelProbeClient client = newClient(
                new LocalDeterministicEmbeddingModelFactory(),
                new LocalDeterministicChatModelFactory());
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "local",
                "local-deterministic",
                "local://deterministic",
                "api_key",
                "local-bge-m3",
                "EMBEDDING",
                8192,
                64,
                Map.of("dimensions", 64),
                "secret"
        );
        var options = EmbeddingOptions.builder()
                .model("local-bge-m3")
                .dimensions(64)
                .build();

        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(request, options));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("仅支持 no_auth");
    }

    @Test
    void rejectsNonCanonicalEndpointForLocalChatRuntime() {
        LocalDeterministicSpringAiModelProbeClient client = newClient(
                new LocalDeterministicEmbeddingModelFactory(),
                new LocalDeterministicChatModelFactory());
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "local",
                "local-deterministic",
                "http://local-deterministic",
                "no_auth",
                "local-chat",
                "CHAT",
                8192,
                null,
                Map.of("temperature", 0.0),
                null
        );
        ChatOptions options = ChatOptions.builder().model("local-chat").temperature(0.0).build();

        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(request, options));

        assertThat(result.connected()).isFalse();
        assertThat(result.message()).contains("local://deterministic");
    }

    @Test
    void rejectsCredentialMaterialEvenWhenLocalRuntimeClaimsNoAuth() {
        LocalDeterministicSpringAiModelProbeClient client = newClient(
                new LocalDeterministicEmbeddingModelFactory(),
                new LocalDeterministicChatModelFactory());
        SpringAiModelRuntimeRequest request = new SpringAiModelRuntimeRequest(
                "local",
                "local-deterministic",
                "local://deterministic",
                "no_auth",
                "local-bge-m3",
                "EMBEDDING",
                8192,
                64,
                Map.of("dimensions", 64),
                "must-not-be-consumed"
        );
        var options = EmbeddingOptions.builder().model("local-bge-m3").dimensions(64).build();

        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(request, options));

        assertThat(result.connected()).isFalse();
        assertThat(result.message())
                .contains("不允许携带凭证材料")
                .doesNotContain("must-not-be-consumed");
    }

    @Test
    void probesLocalDeterministicChatRuntimeThroughSpringAiModel() {
        LocalDeterministicChatModelFactory factory = new LocalDeterministicChatModelFactory();
        LocalDeterministicSpringAiModelProbeClient client = newClient(new LocalDeterministicEmbeddingModelFactory(), factory);
        SpringAiModelRuntimeRequest request = localChatRequest();
        ChatOptions options = ChatOptions.builder().model("local-chat").temperature(0.0).build();

        SpringAiModelConnectivityResult result = client.probe(new SpringAiModelRuntimeProbe(request, options));

        assertThat(client.supports(new SpringAiModelRuntimeProbe(request, options))).isTrue();
        assertThat(result.connected()).isTrue();
        assertThat(result.status()).isEqualTo("PASSED");
        assertThat(result.message()).contains("Spring AI ChatModel");
    }

    @Test
    void producesDeterministicGroundedChatAnswer() {
        LocalDeterministicChatModelFactory factory = new LocalDeterministicChatModelFactory();
        ChatOptions options = ChatOptions.builder().model("local-chat").temperature(0.0).build();
        String prompt = "系统提示\n\n用户问题：报销期限是多少？\n\n请优先基于以下引用原文直接作答；若证据不足，明确说明。"
                + "\n\n引用原文：\n- 文档=制度, 位置=第1段, 分数=0.91\n  原文：差旅报销必须在30天内提交。"
                + "\n\n召回元数据：\n- kb=7";

        String first = factory.create(localChatRequest(), options).call(new Prompt(prompt, options)).getResult().getOutput().getText();
        String second = factory.create(localChatRequest(), options).call(new Prompt(prompt, options)).getResult().getOutput().getText();

        assertThat(first).contains("报销期限是多少", "差旅报销必须在30天内提交", "引用列表");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void emptyCitationBoundaryReturnsFixedInsufficientEvidenceWithoutEchoingInstructions() {
        LocalDeterministicChatModel model = new LocalDeterministicChatModel(
                ChatOptions.builder().model("local-chat").build()
        );
        String sensitiveInstruction = "SYSTEM_SECRET_DO_NOT_ECHO_7f1c";
        String prompt = sensitiveInstruction
                + "\n\n用户问题：报销期限是多少？"
                + "\n\n请优先基于以下引用原文直接作答；若证据不足，明确说明。"
                + "\n\n引用原文：\n\n召回元数据：\n- count=0";

        String answer = model.call(new Prompt(prompt)).getResult().getOutput().getText();

        assertThat(answer)
                .contains("证据不足")
                .doesNotContain(sensitiveInstruction)
                .doesNotContain("已根据引用原文生成回答");
    }

    @Test
    void probeInputWithoutCitationBoundaryReturnsFixedHealthAnswer() {
        LocalDeterministicChatModel model = new LocalDeterministicChatModel(
                ChatOptions.builder().model("local-chat").build()
        );

        String answer = model.call(new Prompt("ping")).getResult().getOutput().getText();

        assertThat(answer).isEqualTo("本地确定性运行时健康检查通过。");
    }

    private static SpringAiModelRuntimeRequest localEmbeddingRequest() {
        return localEmbeddingRequest(64, Map.of("dimensions", 64));
    }

    private static SpringAiModelRuntimeRequest localEmbeddingRequest(
            Integer embeddingDimension,
            Map<String, Object> options
    ) {
        return new SpringAiModelRuntimeRequest(
                "local",
                "local-deterministic",
                "local://deterministic",
                "no_auth",
                "local-bge-m3",
                "EMBEDDING",
                8192,
                embeddingDimension,
                options,
                null
        );
    }

    private static SpringAiModelRuntimeRequest localChatRequest() {
        return new SpringAiModelRuntimeRequest(
                "local",
                "local-deterministic",
                "local://deterministic",
                "no_auth",
                "local-chat",
                "CHAT",
                8192,
                null,
                Map.of("temperature", 0.0, "maxTokens", 256),
                null
        );
    }

    private static LocalDeterministicSpringAiModelProbeClient newClient(
            LocalDeterministicEmbeddingModelFactory embeddingModelFactory,
            LocalDeterministicChatModelFactory chatModelFactory
    ) {
        return new LocalDeterministicSpringAiModelProbeClient(embeddingModelFactory, chatModelFactory);
    }
}
