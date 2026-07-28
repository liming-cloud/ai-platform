package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ArkMultimodalEmbeddingModelTest {

    @Test
    void 应逐条调用多模态接口并保持输入顺序() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v3/embeddings/multimodal", exchange -> {
            var index = calls.getAndIncrement();
            var body = index == 0
                    ? "{\"data\":{\"embedding\":[1.0,0.0]}}"
                    : "{\"data\":{\"embedding\":[0.0,1.0]}}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var deployment = new EmbeddingDeploymentRef(1, 1, 2);
            var model = new ArkMultimodalEmbeddingModel(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3"),
                    "test-key",
                    "doubao-embedding-vision-251215",
                    Duration.ofSeconds(3),
                    deployment);

            var result = model.embed(new EmbeddingRequest(
                    deployment,
                    List.of("第一段", "第二段")));

            assertEquals(2, calls.get());
            assertEquals(1.0f, result.vectors().get(0).values()[0]);
            assertEquals(1.0f, result.vectors().get(1).values()[1]);
        } finally {
            server.stop(0);
        }
    }
}
