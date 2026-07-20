package com.aiplatform.knowledge.infrastructure.springai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;
import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolver;
import com.aiplatform.infra.springai.model.factory.SpringAiChatModelFactory;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.strategy.SpringAiModelOptionsStrategy;
import com.aiplatform.knowledge.domain.port.answer.model.KnowledgeAnswerEvidence;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeConnectionRef;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelRuntimeDescriptor;
import com.aiplatform.shared.kernel.TenantId;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.ChatOptions;

class SpringAiKnowledgeAnswerGenerationAdapterTest {
    @Test
    void providerFailureDoesNotRetainRawCredentialCauseAtTheWebLoggingBoundary() {
        String credential = "sk-live-knowledge-sensitive-value";
        String percentEncoded = URLEncoder.encode(credential, StandardCharsets.UTF_8);
        String base64Encoded = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        SpringAiModelOptionsRegistry optionsRegistry = mock(SpringAiModelOptionsRegistry.class);
        SpringAiModelOptionsStrategy optionsStrategy = mock(SpringAiModelOptionsStrategy.class);
        SpringAiChatModelFactory factory = mock(SpringAiChatModelFactory.class);
        SpringAiRuntimeSecretResolver secretResolver = mock(SpringAiRuntimeSecretResolver.class);
        ChatOptions chatOptions = mock(ChatOptions.class);
        when(optionsRegistry.select(any())).thenReturn(optionsStrategy);
        when(optionsStrategy.buildOptions(any())).thenReturn(chatOptions);
        when(factory.supports(any())).thenReturn(true);
        when(secretResolver.resolveCredentialMaterial(any())).thenReturn(credential);
        when(factory.create(any(), any())).thenThrow(new IllegalStateException(
                "request failed Authorization: Bearer " + credential
                        + "?api_key=" + percentEncoded
                        + " body={\"credential\":\"" + base64Encoded + "\"}"
        ));
        SpringAiKnowledgeAnswerGenerationAdapter adapter = new SpringAiKnowledgeAnswerGenerationAdapter(
                optionsRegistry,
                List.of(factory),
                secretResolver
        );

        assertThatThrownBy(() -> adapter.generateAnswer(
                TenantId.of("2001"),
                runtimeDescriptor(),
                "员工制度",
                List.of(evidence())
        )).isInstanceOfSatisfying(IllegalStateException.class, failure -> {
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getMessage())
                    .contains("答案生成模型调用失败")
                    .contains("***")
                    .doesNotContain(credential, percentEncoded, base64Encoded);
            StringWriter stackTrace = new StringWriter();
            failure.printStackTrace(new PrintWriter(stackTrace));
            assertThat(stackTrace.toString())
                    .doesNotContain(credential, percentEncoded, base64Encoded)
                    .doesNotContain("api_key=", "credential");
        });
        ArgumentCaptor<SpringAiRuntimeSecretResolutionRequest> authorityCaptor =
                ArgumentCaptor.forClass(SpringAiRuntimeSecretResolutionRequest.class);
        verify(secretResolver).resolveCredentialMaterial(authorityCaptor.capture());
        assertThat(authorityCaptor.getValue().providerId()).isEqualTo("2001");
        assertThat(authorityCaptor.getValue().modelConfigId()).isEqualTo("3001");
        assertThat(authorityCaptor.getValue().providerCode()).isEqualTo("openai-main");
        assertThat(authorityCaptor.getValue().modelCode()).isEqualTo("gpt-enterprise");
        assertThat(authorityCaptor.getValue().modelConfigVersion()).isEqualTo(7);
        assertThat(authorityCaptor.getValue().modelType()).isEqualTo("CHAT");
        assertThat(authorityCaptor.getValue().effectiveOptions()).isEqualTo(Map.of("temperature", 0.2));
        assertThat(authorityCaptor.getValue().effectiveOptionsDigest()).isEqualTo("a".repeat(64));
    }

    @Test
    void noAuthAuthorityRejectionPreventsChatClientCreationAndNetworkCall() {
        SpringAiModelOptionsRegistry optionsRegistry = mock(SpringAiModelOptionsRegistry.class);
        SpringAiChatModelFactory factory = mock(SpringAiChatModelFactory.class);
        SpringAiKnowledgeAnswerGenerationAdapter adapter = new SpringAiKnowledgeAnswerGenerationAdapter(
                optionsRegistry,
                List.of(factory),
                request -> {
                    throw new IllegalStateException("模型运行时 authority 校验失败");
                }
        );

        assertThatThrownBy(() -> adapter.generateAnswer(
                TenantId.of("2001"),
                noAuthRuntimeDescriptor(),
                "员工制度",
                List.of(evidence())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authority 校验失败");
        verifyNoInteractions(optionsRegistry, factory);
    }

    private static KnowledgeModelRuntimeDescriptor runtimeDescriptor() {
        return new KnowledgeModelRuntimeDescriptor(
                "3001",
                "2001",
                "openai-main",
                "openai_compatible",
                new KnowledgeModelRuntimeConnectionRef(
                        "https://api.example.com/v1",
                        "api_key",
                        "secret-1",
                        "sk-***"
                ),
                "gpt-enterprise",
                7,
                "CHAT",
                Set.of(KnowledgeModelCapability.CHAT),
                Map.of("temperature", 0.2),
                "a".repeat(64),
                "mdl-3001-v1"
        );
    }

    private static KnowledgeModelRuntimeDescriptor noAuthRuntimeDescriptor() {
        return new KnowledgeModelRuntimeDescriptor(
                "3002",
                "2002",
                "local-runtime",
                "local-deterministic",
                new KnowledgeModelRuntimeConnectionRef("local://deterministic", "no_auth", null, null),
                "local-chat",
                7,
                "CHAT",
                Set.of(KnowledgeModelCapability.CHAT),
                Map.of(),
                "a".repeat(64),
                "mdl-3002-v1"
        );
    }

    private static KnowledgeAnswerEvidence evidence() {
        return new KnowledgeAnswerEvidence(
                "1001",
                "2001",
                "chunk-1",
                "试用期为三个月。",
                "kb://employee-policy",
                "员工制度/试用期",
                3,
                null,
                true
        );
    }
}
