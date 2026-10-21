package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilityCallException;
import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilityCallException.Reason;
import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/** 平台样本专用 SDK 传输桥；限制一次实际发送、固定目标、正文大小和取消后的资源寿命。 */
final class PlatformSampleHttpClient implements HttpClient {
    private static final long MAX_RESPONSE_BYTES = 1_048_576;
    private final URI target;
    private final OkHttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean delegated = new AtomicBoolean();
    private volatile okhttp3.Call active;
    private volatile Thread watcher;

    PlatformSampleHttpClient(URI target, Duration timeout) {
        this.target = target;
        var sent = new AtomicBoolean();
        client = new OkHttpClient.Builder().retryOnConnectionFailure(false)
                .followRedirects(false).followSslRedirects(false)
                .connectTimeout(timeout).readTimeout(timeout).writeTimeout(timeout).callTimeout(timeout)
                .addNetworkInterceptor(chain -> {
                    if (!sent.compareAndSet(false, true)) throw failure(Reason.RETRY_BLOCKED);
                    return chain.proceed(chain.request());
                }).build();
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        if (!target.equals(URI.create(request.url()))) throw failure(Reason.ENDPOINT_REJECTED);
        byte[] bytes = null;
        if (request.body() != null) {
            try (var output = new ByteArrayOutputStream()) {
                request.body().writeTo(output);
                bytes = output.toByteArray();
                if (bytes.length > 16_384) throw failure(Reason.RESPONSE_INVALID);
            } catch (IOException exception) { throw failure(Reason.CALL_FAILED); }
        }
        var builder = new Request.Builder().url(request.url()).method(request.method().name(),
                bytes == null ? null : RequestBody.create(bytes, MediaType.get("application/json")));
        for (var name : request.headers().names())
            for (var value : request.headers().values(name)) builder.addHeader(name, value);
        return send(builder.build());
    }

    HttpResponse postJson(String key, byte[] body) {
        return send(new Request.Builder().url(target.toString()).post(RequestBody.create(body, MediaType.get("application/json")))
                .header("Authorization", "Bearer " + key).header("Accept", "application/json").build());
    }

    private HttpResponse send(Request request) {
        if (closed.get() || Thread.currentThread().isInterrupted()) throw failure(Reason.CANCELLED);
        if (!delegated.compareAndSet(false, true)) throw failure(Reason.RETRY_BLOCKED);
        active = client.newCall(request);
        var callingThread = Thread.currentThread();
        // SDK 的同步读取可能不及时响应 Future.cancel；只监测本次调用线程，不扫描或终止其他请求。
        watcher = Thread.ofVirtual().start(() -> {
            while (!closed.get()) {
                if (callingThread.isInterrupted()) { var call = active; if (call != null) call.cancel(); return; }
                LockSupport.parkNanos(5_000_000L);
            }
        });
        try {
            var response = active.execute();
            if (response.code() != 200) {
                int code = response.code(); response.close();
                throw failure(code == 401 || code == 403 ? Reason.AUTHENTICATION_FAILED
                        : code == 429 ? Reason.RATE_LIMITED : Reason.CALL_FAILED);
            }
            var body = response.body();
            if (body == null || body.contentLength() > MAX_RESPONSE_BYTES) { response.close(); throw failure(Reason.RESPONSE_INVALID); }
            var headers = Headers.builder().putAll(response.headers().toMultimap()).build();
            var stream = new FilterInputStream(body.byteStream()) {
                private long count;
                private void count(int size) throws IOException {
                    if (size > 0 && (count += size) > MAX_RESPONSE_BYTES) {
                        response.close(); throw new IOException("平台样本响应超出上限");
                    }
                }
                @Override public int read() throws IOException { int value = in.read(); count(value < 0 ? 0 : 1); return value; }
                @Override public int read(byte[] values, int start, int length) throws IOException {
                    int size = in.read(values, start, length); count(size); return size;
                }
            };
            return new HttpResponse() {
                @Override public int statusCode() { return 200; }
                @Override public Headers headers() { return headers; }
                @Override public InputStream body() { return stream; }
                @Override public void close() { response.close(); }
            };
        } catch (java.io.InterruptedIOException exception) {
            throw failure(callingThread.isInterrupted() ? Reason.CANCELLED : Reason.TIMEOUT);
        } catch (IOException exception) { throw failure(callingThread.isInterrupted() ? Reason.CANCELLED : Reason.CALL_FAILED); }
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        // 本合同没有流式或异步协议，不能为满足 SDK 接口而启动另一条未受预算约束的传输。
        return CompletableFuture.failedFuture(failure(Reason.UNSUPPORTED));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        var call = active; if (call != null) call.cancel();
        var thread = watcher; if (thread != null) thread.interrupt();
        client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown();
    }

    private static PlatformCapabilityCallException failure(Reason reason) { return new PlatformCapabilityCallException(reason); }
}
