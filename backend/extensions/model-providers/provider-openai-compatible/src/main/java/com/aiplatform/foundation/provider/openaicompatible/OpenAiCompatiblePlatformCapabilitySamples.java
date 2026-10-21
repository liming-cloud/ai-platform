package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilityCallException;
import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilityCallException.Reason;
import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformCapabilitySampleRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilitySampleExecutor;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformReranker;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilityCredentialResolver;
import com.aiplatform.foundation.security.egress.OutboundEndpointPolicy;
import com.aiplatform.foundation.security.egress.UnsafeOutboundEndpointException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.LogLevel;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;

/** 平台标准样本与独立硅基流动重排适配，预算及通过判定归模型上下文。 */
public final class OpenAiCompatiblePlatformCapabilitySamples implements PlatformCapabilitySampleExecutor, PlatformReranker {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final PlatformCapabilityCredentialResolver credentials;
    private final OutboundEndpointPolicy egress;

    public OpenAiCompatiblePlatformCapabilitySamples(PlatformCapabilityCredentialResolver credentials, OutboundEndpointPolicy egress) {
        this.credentials = Objects.requireNonNull(credentials, "平台凭据解析器不能为空");
        this.egress = Objects.requireNonNull(egress, "平台出站策略不能为空");
    }
    @Override public ProviderKind providerKind() { return ProviderKind.OPENAI_COMPATIBLE; }

    @Override
    public PlatformCapabilitySampleResult execute(PlatformCapabilitySampleRequest request) {
        Objects.requireNonNull(request, "平台样本不能为空");
        if (request.type() != ModelType.CHAT && request.type() != ModelType.EMBEDDING) throw failure(Reason.UNSUPPORTED);
        return withSecret(request, (key, timeout) -> standard(request, key, timeout));
    }

    @Override
    public PlatformCapabilitySampleResult rerank(PlatformCapabilitySampleRequest request) {
        Objects.requireNonNull(request, "平台样本不能为空");
        if (request.type() != ModelType.RERANK || !request.providerPresetId().equals("SILICONFLOW")) throw failure(Reason.UNSUPPORTED);
        return withSecret(request, (key, timeout) -> rerankSample(request, key, timeout));
    }

    private PlatformCapabilitySampleResult withSecret(PlatformCapabilitySampleRequest request,
            java.util.function.BiFunction<String, java.time.Duration, PlatformCapabilitySampleResult> call) {
        long started = System.nanoTime();
        try { egress.verify(request.endpoint()); }
        catch (UnsafeOutboundEndpointException exception) { throw failure(Reason.ENDPOINT_REJECTED); }
        final com.aiplatform.foundation.security.secret.SecretValue secret;
        try { secret = Objects.requireNonNull(credentials.resolve(request.credentialReference(), request.connectionRevision())); }
        catch (RuntimeException exception) { throw failure(Reason.SECRET_UNAVAILABLE); }
        try (secret) {
            if (Thread.currentThread().isInterrupted()) throw failure(Reason.CANCELLED);
            long remaining = request.timeout().toNanos() - (System.nanoTime() - started);
            if (remaining <= 0) throw failure(Reason.TIMEOUT);
            return secret.use(chars -> call.apply(new String(chars), java.time.Duration.ofNanos(remaining)));
        }
        catch (RuntimeException exception) { throw normalized(exception); }
    }

    private static PlatformCapabilitySampleResult standard(PlatformCapabilitySampleRequest request, String key, java.time.Duration timeout) {
        var endpoint = URI.create(request.endpoint().toString().replaceAll("/+$", "")
                + (request.type() == ModelType.CHAT ? "/chat/completions" : "/embeddings"));
        try (var transport = new PlatformSampleHttpClient(endpoint, timeout)) {
            var client = new OpenAIClientImpl(ClientOptions.builder().httpClient(transport).apiKey(key)
                    .baseUrl(request.endpoint().toString()).maxRetries(0).logLevel(LogLevel.OFF).timeout(timeout).build());
            try {
            if (request.type() == ModelType.CHAT) {
                var options = OpenAiChatOptions.builder().model(request.modelCode()).maxCompletionTokens(request.maxOutputTokens())
                        .maxRetries(0).timeout(timeout).extraBody(request.providerPresetId().equals("VOLCENGINE")
                                ? Map.of("thinking", Map.of("type", "disabled")) : Map.of()).build();
                var model = OpenAiChatModel.builder().openAiClient(client).openAiClientAsync(client.async()).options(options).build();
                var response = model.call(new Prompt(request.input()));
                if (response == null || response.getResults().size() != 1) throw failure(Reason.RESPONSE_INVALID);
                var usage = response.getMetadata().getUsage();
                if (usage == null || usage.getNativeUsage() == null || usage.getPromptTokens() <= 0) throw failure(Reason.RESPONSE_INVALID);
                var generation = response.getResult();
                return new PlatformCapabilitySampleResult(request.binding(), request.type(), generation.getOutput().getText(),
                        List.of(), List.of(), usage.getPromptTokens().longValue(), usage.getCompletionTokens().longValue(),
                        !"stop".equalsIgnoreCase(generation.getMetadata().getFinishReason())
                                || generation.getOutput().hasToolCalls() || !generation.getOutput().getMedia().isEmpty());
            }
            var options = OpenAiEmbeddingOptions.builder().model(request.modelCode()).maxRetries(0).timeout(timeout).build();
            var model = OpenAiEmbeddingModel.builder().openAiClient(client).options(options).build();
            // BGE 不支持 dimensions 请求参数；检查真实输出维度，不发送一个可能被忽略的声明值。
            var response = model.call(new EmbeddingRequest(List.of(request.input()), options));
            var usage = response.getMetadata().getUsage();
            if (usage == null || usage.getNativeUsage() == null || usage.getPromptTokens() <= 0) throw failure(Reason.RESPONSE_INVALID);
            var embeddings = response.getResults().stream().map(item -> {
                var values = new ArrayList<Double>();
                for (float value : item.getOutput()) values.add((double) value);
                return new PlatformCapabilitySampleResult.Embedding(item.getIndex(), values);
            }).toList();
            return new PlatformCapabilitySampleResult(request.binding(), request.type(), null, embeddings, List.of(),
                    usage.getPromptTokens().longValue(), 0L, false);
            } finally { client.close(); }
        }
    }

    private static PlatformCapabilitySampleResult rerankSample(PlatformCapabilitySampleRequest request, String key, java.time.Duration timeout) {
        try (var transport = new PlatformSampleHttpClient(request.endpoint(), timeout)) {
            var body = JSON.writeValueAsBytes(Map.of("model", request.modelCode(), "query", request.input(),
                    "documents", request.documents(), "top_n", 3, "return_documents", false));
            try (var response = transport.postJson(key, body)) {
                var root = JSON.readTree(response.body());
                var results = root == null ? null : root.get("results");
                var tokens = root == null ? null : root.path("meta").path("tokens");
                if (results == null || !results.isArray() || results.size() != 3 || tokens == null
                        || !tokens.path("input_tokens").isIntegralNumber() || !tokens.path("input_tokens").canConvertToLong()
                        || tokens.path("input_tokens").longValue() <= 0 || !tokens.path("output_tokens").isIntegralNumber()
                        || !tokens.path("output_tokens").canConvertToLong()
                        || tokens.path("output_tokens").longValue() != 0) throw failure(Reason.RESPONSE_INVALID);
                var ranks = new ArrayList<PlatformCapabilitySampleResult.Ranked>();
                for (var item : results) {
                    if (!item.path("index").isIntegralNumber() || !item.path("index").canConvertToInt()
                            || !item.path("relevance_score").isNumber()) throw failure(Reason.RESPONSE_INVALID);
                    ranks.add(new PlatformCapabilitySampleResult.Ranked(item.path("index").intValue(), item.path("relevance_score").doubleValue()));
                }
                return new PlatformCapabilitySampleResult(request.binding(), request.type(), null, List.of(), ranks,
                        tokens.path("input_tokens").longValue(), 0L, false);
            }
        } catch (java.io.InterruptedIOException exception) {
            throw failure(Thread.currentThread().isInterrupted() ? Reason.CANCELLED : Reason.TIMEOUT);
        }
        catch (java.io.IOException exception) {
            // 取消正文读取也可能抛普通 SocketException；不能依赖异常子类型丢失取消语义。
            throw failure(Thread.currentThread().isInterrupted() ? Reason.CANCELLED : Reason.RESPONSE_INVALID);
        }
    }

    private static PlatformCapabilityCallException normalized(RuntimeException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof PlatformCapabilityCallException failure) return new PlatformCapabilityCallException(failure.reason());
            if (cause instanceof java.io.InterruptedIOException) return failure(Reason.TIMEOUT);
        }
        return failure(Thread.currentThread().isInterrupted() ? Reason.CANCELLED : Reason.CALL_FAILED);
    }
    private static PlatformCapabilityCallException failure(Reason reason) { return new PlatformCapabilityCallException(reason); }
}
