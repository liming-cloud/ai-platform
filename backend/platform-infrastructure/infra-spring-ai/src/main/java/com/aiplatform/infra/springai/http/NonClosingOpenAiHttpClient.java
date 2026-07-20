package com.aiplatform.infra.springai.http;

import com.openai.core.RequestOptions;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 让短生命周期 SDK client 复用由 {@link SecureModelHttpTransport} 统一管理的连接池。
 */
final class NonClosingOpenAiHttpClient implements com.openai.core.http.HttpClient {
    private final com.openai.core.http.HttpClient delegate;

    NonClosingOpenAiHttpClient(com.openai.core.http.HttpClient delegate) {
        this.delegate = Objects.requireNonNull(delegate, "OpenAI HTTP 客户端不能为空");
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        return delegate.execute(request, options);
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        return delegate.executeAsync(request, options);
    }

    @Override
    public void close() {
        // 共享底层客户端由 SecureModelHttpTransport.close() 统一关闭。
    }
}
