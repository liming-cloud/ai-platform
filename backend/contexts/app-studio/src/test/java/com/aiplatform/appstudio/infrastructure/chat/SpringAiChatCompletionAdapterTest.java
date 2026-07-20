package com.aiplatform.appstudio.infrastructure.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.chat.ModelInvocationAuditPort;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeConnectionRef;
import com.aiplatform.appstudio.domain.port.chat.model.AppModelRuntimeDescriptor;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionRequest;
import com.aiplatform.appstudio.domain.port.chat.model.ChatCompletionResult;
import com.aiplatform.appstudio.domain.port.chat.model.ModelInvocationAuditRecord;
import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;
import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolver;
import com.aiplatform.infra.springai.model.factory.SpringAiChatModelFactory;
import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.strategy.SpringAiModelOptionsStrategy;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.ChatOptions;

class SpringAiChatCompletionAdapterTest {
    @Test
    void completeSanitizesKnownCredentialRepresentationsBeforePersistingFailure() {
        String credential = "sk-live-sensitive-value";
        String encoded = URLEncoder.encode(credential, StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        SpringAiModelOptionsRegistry optionsRegistry = mock(SpringAiModelOptionsRegistry.class);
        SpringAiModelOptionsStrategy optionsStrategy = mock(SpringAiModelOptionsStrategy.class);
        SpringAiChatModelFactory factory = mock(SpringAiChatModelFactory.class);
        SpringAiRuntimeSecretResolver secretResolver = mock(SpringAiRuntimeSecretResolver.class);
        ModelInvocationAuditPort auditPort = mock(ModelInvocationAuditPort.class);
        ChatOptions chatOptions = mock(ChatOptions.class);
        when(optionsRegistry.select(any())).thenReturn(optionsStrategy);
        when(optionsStrategy.buildOptions(any())).thenReturn(chatOptions);
        when(factory.supports(any())).thenReturn(true);
        when(secretResolver.resolveCredentialMaterial(any())).thenReturn(credential);
        when(factory.create(any(), any())).thenThrow(new IllegalStateException(
                "Authorization: Bearer " + credential
                        + " endpoint?api_key=" + encoded
                        + " body={\"credential\":\"" + base64 + "\"}"
        ));
        when(auditPort.record(any())).thenReturn("100");
        SpringAiChatCompletionAdapter adapter = new SpringAiChatCompletionAdapter(
                optionsRegistry,
                List.of(factory),
                secretResolver,
                auditPort,
                Clock.fixed(Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC)
        );

        ChatCompletionResult result = adapter.complete(credentialRequest());

        ArgumentCaptor<ModelInvocationAuditRecord> recordCaptor = ArgumentCaptor.forClass(ModelInvocationAuditRecord.class);
        verify(auditPort).record(recordCaptor.capture());
        ArgumentCaptor<SpringAiRuntimeSecretResolutionRequest> authorityCaptor =
                ArgumentCaptor.forClass(SpringAiRuntimeSecretResolutionRequest.class);
        verify(secretResolver).resolveCredentialMaterial(authorityCaptor.capture());
        assertThat(authorityCaptor.getValue().providerId()).isEqualTo("3001");
        assertThat(authorityCaptor.getValue().modelConfigId()).isEqualTo("6");
        assertThat(authorityCaptor.getValue().endpoint()).isEqualTo("https://example.invalid");
        assertThat(authorityCaptor.getValue().authType()).isEqualTo("api_key");
        assertThat(authorityCaptor.getValue().modelConfigVersion()).isEqualTo(7);
        assertThat(authorityCaptor.getValue().modelType()).isEqualTo("CHAT");
        assertThat(authorityCaptor.getValue().effectiveOptions()).isEqualTo(Map.of());
        assertThat(authorityCaptor.getValue().effectiveOptionsDigest()).isEqualTo("a".repeat(64));
        assertThat(recordCaptor.getValue().errorMessage())
                .doesNotContain(credential, encoded, base64)
                .contains("***");
        assertThat(result.auditAttributes().get("errorMessage"))
                .doesNotContain(credential, encoded, base64)
                .doesNotContain("api_key=", "credential");
    }

    @Test
    void completeReturnsFailedResultAndAuditLogWhenChatModelInvocationFails() {
        SpringAiModelOptionsRegistry optionsRegistry = mock(SpringAiModelOptionsRegistry.class);
        SpringAiModelOptionsStrategy optionsStrategy = mock(SpringAiModelOptionsStrategy.class);
        SpringAiChatModelFactory factory = mock(SpringAiChatModelFactory.class);
        SpringAiRuntimeSecretResolver secretResolver = mock(SpringAiRuntimeSecretResolver.class);
        ModelInvocationAuditPort auditPort = mock(ModelInvocationAuditPort.class);
        ChatOptions chatOptions = mock(ChatOptions.class);
        when(optionsRegistry.select(any())).thenReturn(optionsStrategy);
        when(optionsStrategy.buildOptions(any())).thenReturn(chatOptions);
        when(factory.supports(any())).thenReturn(true);
        when(factory.create(any(), any())).thenThrow(new IllegalStateException("simulated model outage with apiKey=secret-value"));
        when(auditPort.record(any())).thenReturn("99");
        SpringAiChatCompletionAdapter adapter = new SpringAiChatCompletionAdapter(
                optionsRegistry,
                List.of(factory),
                secretResolver,
                auditPort,
                Clock.fixed(Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC)
        );

        ChatCompletionResult result = adapter.complete(request());

        assertThat(result.answerable()).isFalse();
        assertThat(result.modelInvocationLogId()).isEqualTo("99");
        assertThat(result.auditAttributes())
                .containsEntry("invocationStatus", "FAILED")
                .containsEntry("decisionSource", "model-invocation-failed")
                .containsEntry("errorCode", "IllegalStateException")
                .containsEntry("modelInvocationLogId", "99")
                .containsEntry("providerCode", "siliconflow")
                .containsEntry("modelCode", "Qwen/Qwen2.5-7B-Instruct")
                .containsEntry("traceId", "trace-failed-1")
                .containsEntry("sourceContext", "APP_STUDIO")
                .containsEntry("sourceResourceType", "AI_APPLICATION")
                .containsEntry("sourceResourceId", "10");
        assertThat(result.auditAttributes().get("errorMessage"))
                .contains("simulated model outage")
                .doesNotContain("apiKey=")
                .doesNotContain("secret-value");
        ArgumentCaptor<ModelInvocationAuditRecord> recordCaptor = ArgumentCaptor.forClass(ModelInvocationAuditRecord.class);
        verify(auditPort).record(recordCaptor.capture());
        assertThat(recordCaptor.getValue().status()).isEqualTo("FAILED");
        assertThat(recordCaptor.getValue().errorCode()).isEqualTo("IllegalStateException");
        assertThat(recordCaptor.getValue().traceId()).isEqualTo("trace-failed-1");
        assertThat(recordCaptor.getValue().idempotencyKey()).isEqualTo("client-message-1");
    }

    @Test
    void noAuthAuthorityRejectionReturnsFailureWithoutCreatingAChatClient() {
        SpringAiModelOptionsRegistry optionsRegistry = mock(SpringAiModelOptionsRegistry.class);
        SpringAiChatModelFactory factory = mock(SpringAiChatModelFactory.class);
        SpringAiRuntimeSecretResolver secretResolver = request -> {
            throw new IllegalStateException("模型运行时 authority 校验失败");
        };
        ModelInvocationAuditPort auditPort = mock(ModelInvocationAuditPort.class);
        when(auditPort.record(any())).thenReturn("101");
        SpringAiChatCompletionAdapter adapter = new SpringAiChatCompletionAdapter(
                optionsRegistry,
                List.of(factory),
                secretResolver,
                auditPort,
                Clock.fixed(Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC)
        );

        ChatCompletionResult result = adapter.complete(request());

        assertThat(result.answerable()).isFalse();
        assertThat(result.auditAttributes().get("errorMessage")).contains("authority 校验失败");
        verifyNoInteractions(optionsRegistry, factory);
    }

    private static ChatCompletionRequest request() {
        return new ChatCompletionRequest(
                TenantId.of("2001"),
                "10",
                "1001",
                "trace-failed-1",
                "client-message-1",
                "你是企业制度助手",
                "请根据引用回答差旅制度",
                List.of(citation()),
                new AppModelRuntimeDescriptor(
                        "6",
                        "3001",
                        "siliconflow",
                        "openai-compatible",
                        new AppModelRuntimeConnectionRef("https://example.invalid", "no_auth", null),
                        "Qwen/Qwen2.5-7B-Instruct",
                        7,
                        "CHAT",
                        Set.of("CHAT"),
                        Map.of(),
                        "a".repeat(64),
                        "policy-v1"
                )
        );
    }

    private static ChatCompletionRequest credentialRequest() {
        return new ChatCompletionRequest(
                TenantId.of("2001"),
                "10",
                "1001",
                "trace-failed-secret",
                "client-message-secret",
                "你是企业制度助手",
                "请根据引用回答差旅制度",
                List.of(citation()),
                new AppModelRuntimeDescriptor(
                        "6",
                        "3001",
                        "siliconflow",
                        "openai-compatible",
                        new AppModelRuntimeConnectionRef("https://example.invalid", "api_key", "secret-1"),
                        "Qwen/Qwen2.5-7B-Instruct",
                        7,
                        "CHAT",
                        Set.of("CHAT"),
                        Map.of(),
                        "a".repeat(64),
                        "policy-v1"
                )
        );
    }

    private static Citation citation() {
        return new Citation("19", "15", "15", "差旅制度", "差旅报销规则", "chunk-15", 0.8);
    }
}
