package com.aiplatform.foundation.provider.openaicompatible;

import com.aiplatform.foundation.modelruntime.application.runtime.facade.EmbeddingModelPort;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingBatch;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingDeploymentRef;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingRequest;
import com.aiplatform.foundation.modelruntime.application.runtime.facade.dto.embedding.EmbeddingVector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 方舟多模态嵌入协议适配。该协议的每次调用只返回一个组合向量，因此 RAG 文本必须逐条请求。
 */
final class ArkMultimodalEmbeddingModel implements EmbeddingModelPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String modelCode;
    private final Duration timeout;
    private final EmbeddingDeploymentRef deployment;

    ArkMultimodalEmbeddingModel(
            URI baseEndpoint,
            String apiKey,
            String modelCode,
            Duration timeout,
            EmbeddingDeploymentRef deployment) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.endpoint = URI.create(stripTrailingSlash(baseEndpoint) + "/embeddings/multimodal");
        this.apiKey = Objects.requireNonNull(apiKey, "方舟 API Key 不能为空");
        this.modelCode = Objects.requireNonNull(modelCode, "方舟嵌入模型不能为空");
        this.timeout = Objects.requireNonNull(timeout, "方舟嵌入超时不能为空");
        this.deployment = Objects.requireNonNull(deployment, "嵌入部署不能为空");
    }

    @Override
    public EmbeddingBatch embed(EmbeddingRequest request) {
        Objects.requireNonNull(request, "嵌入请求不能为空");
        if (!deployment.equals(request.deployment())) {
            throw new IllegalArgumentException("嵌入请求绑定的模型部署不匹配");
        }
        var vectors = new ArrayList<EmbeddingVector>(request.texts().size());
        for (var index = 0; index < request.texts().size(); index++) {
            vectors.add(new EmbeddingVector(index, embedOne(request.texts().get(index))));
        }
        return new EmbeddingBatch(deployment, vectors);
    }

    private float[] embedOne(String text) {
        try {
            var input = JSON.createObjectNode();
            input.put("model", modelCode);
            var values = input.putArray("input");
            values.addObject().put("type", "text").put("text", text);
            var response = client.send(
                    HttpRequest.newBuilder(endpoint)
                            .timeout(timeout)
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(input)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("方舟多模态嵌入调用失败，HTTP 状态：" + response.statusCode());
            }
            return parse(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("方舟多模态嵌入调用失败", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("方舟多模态嵌入调用被中断", exception);
        }
    }

    private float[] parse(String body) throws IOException {
        JsonNode values = JSON.readTree(body).path("data").path("embedding");
        if (!values.isArray() || values.size() != deployment.dimension()) {
            throw new IllegalStateException("方舟多模态嵌入维度不符合冻结配置");
        }
        var vector = new float[values.size()];
        for (var index = 0; index < vector.length; index++) {
            if (!values.get(index).isNumber()) {
                throw new IllegalStateException("方舟多模态嵌入结果格式不正确");
            }
            vector[index] = values.get(index).floatValue();
        }
        return vector;
    }

    private static String stripTrailingSlash(URI value) {
        return value.toString().replaceFirst("/+$", "");
    }
}
