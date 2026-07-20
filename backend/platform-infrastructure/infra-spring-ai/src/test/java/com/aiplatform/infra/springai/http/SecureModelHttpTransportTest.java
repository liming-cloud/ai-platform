package com.aiplatform.infra.springai.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.security.net.OutboundEndpointPolicy;
import com.aiplatform.shared.security.net.StrictOutboundEndpointPolicy;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecureModelHttpTransportTest {
    @Test
    void reResolvesDnsAtConnectionAndRejectsRebindingAnswer() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        StrictOutboundEndpointPolicy policy = new StrictOutboundEndpointPolicy(host -> {
            String address = resolutions.getAndIncrement() == 0 ? "93.184.216.34" : "169.254.169.254";
            return new InetAddress[]{InetAddress.getByName(address)};
        });

        try (SecureModelHttpTransport transport = new SecureModelHttpTransport(policy)) {
            transport.validateEndpoint("https://provider.example/v1");

            Request request = new Request.Builder().url("https://provider.example/v1/models").build();
            assertThatThrownBy(() -> transport.okHttpClientForTesting().newCall(request).execute())
                    .isInstanceOf(UnknownHostException.class)
                    .hasMessageContaining("非公网地址");
            assertThat(resolutions).hasValue(2);
        }
    }

    @Test
    void openAiTransportDisablesRedirectsRetriesAndSystemProxy() {
        try (SecureModelHttpTransport transport = new SecureModelHttpTransport(permissiveLoopbackPolicy())) {
            assertThat(transport.okHttpClientForTesting().followRedirects()).isFalse();
            assertThat(transport.okHttpClientForTesting().followSslRedirects()).isFalse();
            assertThat(transport.okHttpClientForTesting().retryOnConnectionFailure()).isFalse();
            assertThat(transport.okHttpClientForTesting().proxy()).isEqualTo(Proxy.NO_PROXY);
        }
    }

    @Test
    void closingOneSdkClientDoesNotShutdownSharedSecureTransport() {
        try (SecureModelHttpTransport transport = new SecureModelHttpTransport(permissiveLoopbackPolicy())) {
            var client = transport.openAiClient("https://provider.example/v1", "sk-test");

            client.close();

            assertThat(transport.okHttpClientForTesting().dispatcher().executorService().isShutdown()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void apacheSyncTransportDoesNotFollowRedirectHop(int status) throws IOException {
        AtomicInteger redirectedRequests = new AtomicInteger();
        HttpServer target = responseServer(redirectedRequests);
        HttpServer redirect = redirectServer(target, status);
        try (SecureModelHttpTransport transport = new SecureModelHttpTransport(permissiveLoopbackPolicy())) {
            var response = transport.restClientBuilder()
                    .baseUrl(endpoint(redirect))
                    .build()
                    .get()
                    .uri("/redirect")
                    .retrieve()
                    .toBodilessEntity();

            assertThat(response.getStatusCode().value()).isEqualTo(status);
            assertThat(redirectedRequests).hasValue(0);
        } finally {
            redirect.stop(0);
            target.stop(0);
        }
    }

    @Test
    void apacheAsyncTransportDoesNotFollowRedirectHop() throws IOException {
        AtomicInteger redirectedRequests = new AtomicInteger();
        HttpServer target = responseServer(redirectedRequests);
        HttpServer redirect = redirectServer(target, 307);
        try (SecureModelHttpTransport transport = new SecureModelHttpTransport(permissiveLoopbackPolicy())) {
            var response = transport.webClientBuilder()
                    .baseUrl(endpoint(redirect))
                    .build()
                    .get()
                    .uri("/redirect")
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode().value()).isEqualTo(307);
            assertThat(redirectedRequests).hasValue(0);
        } finally {
            redirect.stop(0);
            target.stop(0);
        }
    }

    private static HttpServer responseServer(AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/target", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static HttpServer redirectServer(HttpServer target, int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", endpoint(target) + "/target");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static OutboundEndpointPolicy permissiveLoopbackPolicy() {
        return new OutboundEndpointPolicy() {
            @Override
            public URI validateAndNormalize(String endpoint) {
                return URI.create(endpoint);
            }

            @Override
            public List<InetAddress> resolveForConnection(String hostname) throws UnknownHostException {
                return List.of(InetAddress.getByName(hostname));
            }
        };
    }
}
