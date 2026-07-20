package com.aiplatform.infra.springai.http;

import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Dns;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 模型供应商统一 HTTP 传输层。
 *
 * <p>URI 在模型构造前校验；OkHttp 与 Apache 客户端在每次新建连接时重新解析 DNS，
 * 并直接使用同一批已校验地址建立 socket，从而避免预解析与实际建连之间的 TOCTOU。
 * 所有自动重定向、自动重试和系统代理均关闭。</p>
 */
@Component
public final class SecureModelHttpTransport implements AutoCloseable {
    private final OutboundEndpointPolicy endpointPolicy;
    private final okhttp3.OkHttpClient okHttpClient;
    private final com.openai.core.http.HttpClient openAiHttpClient;
    private final CloseableHttpClient apacheClient;
    private final CloseableHttpAsyncClient apacheAsyncClient;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SecureModelHttpTransport(OutboundEndpointPolicy endpointPolicy) {
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "模型出站地址策略不能为空");
        Dns policyDns = hostname -> endpointPolicy.resolveForConnection(hostname);
        this.okHttpClient = new okhttp3.OkHttpClient.Builder()
                .dns(policyDns)
                .proxy(Proxy.NO_PROXY)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
        this.openAiHttpClient = new NonClosingOpenAiHttpClient(
                new com.openai.client.okhttp.OkHttpClient(okHttpClient)
        );

        DnsResolver apacheDns = new EndpointPolicyDnsResolver(endpointPolicy);
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(apacheDns)
                .build();
        this.apacheClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();

        var asyncConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDnsResolver(apacheDns)
                .build();
        this.apacheAsyncClient = HttpAsyncClients.custom()
                .setConnectionManager(asyncConnectionManager)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
        this.apacheAsyncClient.start();
    }

    public URI validateEndpoint(String endpoint) {
        return endpointPolicy.validateAndNormalize(endpoint);
    }

    public OpenAIClient openAiClient(String endpoint, String apiKey) {
        URI safeEndpoint = validateEndpoint(endpoint);
        ClientOptions options = baseOpenAiOptions(safeEndpoint)
                .apiKey(Objects.requireNonNull(apiKey, "OpenAI API Key 不能为空"))
                .build();
        return new OpenAIClientImpl(options);
    }

    public OpenAIClient azureOpenAiClient(
            String endpoint,
            String apiKey,
            AzureOpenAIServiceVersion serviceVersion
    ) {
        URI safeEndpoint = validateEndpoint(endpoint);
        ClientOptions options = baseOpenAiOptions(safeEndpoint)
                .credential(AzureApiKeyCredential.create(Objects.requireNonNull(apiKey, "Azure OpenAI API Key 不能为空")))
                .azureServiceVersion(Objects.requireNonNull(serviceVersion, "Azure OpenAI API Version 不能为空"))
                .build();
        return new OpenAIClientImpl(options);
    }

    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(new HttpComponentsClientHttpRequestFactory(apacheClient));
    }

    public WebClient.Builder webClientBuilder() {
        return WebClient.builder().clientConnector(new HttpComponentsClientHttpConnector(apacheAsyncClient));
    }

    okhttp3.OkHttpClient okHttpClientForTesting() {
        return okHttpClient;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        okHttpClient.dispatcher().cancelAll();
        okHttpClient.connectionPool().evictAll();
        okHttpClient.dispatcher().executorService().shutdown();
        IOException failure = null;
        try {
            apacheAsyncClient.close();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            apacheClient.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("关闭模型 HTTP 传输失败", failure);
        }
    }

    private ClientOptions.Builder baseOpenAiOptions(URI endpoint) {
        return ClientOptions.builder()
                .httpClient(openAiHttpClient)
                .baseUrl(endpoint.toString())
                .maxRetries(0)
                .putHeader("User-Agent", "ai-platform-secure-model-client");
    }
}
