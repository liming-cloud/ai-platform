package com.aiplatform.infra.springai.model;

import com.aiplatform.infra.springai.model.registry.SpringAiModelOptionsRegistry;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;
import com.aiplatform.infra.springai.model.result.SpringAiModelValidationResult;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelProbeClient;
import com.aiplatform.infra.springai.model.runtime.SpringAiModelRuntimeProbe;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.model.ModelOptions;
import org.springframework.stereotype.Component;

/**
 * Spring AI 模型运行时网关，统一暴露 options 校验、构建和连通性探测能力。
 */
@Component
public class SpringAiModelRuntimeGateway {
    private final SpringAiModelOptionsRegistry registry;
    private final List<SpringAiModelProbeClient> probeClients;

    public SpringAiModelRuntimeGateway(
        SpringAiModelOptionsRegistry registry,
        List<SpringAiModelProbeClient> probeClients
    ) {
        this.registry = Objects.requireNonNull(registry, "模型 options 策略注册表不能为空");
        this.probeClients = copyRequiredList(probeClients, "Spring AI 模型探测客户端");
    }

    public SpringAiModelValidationResult validate(SpringAiModelRuntimeRequest request) {
        String migrationMessage = removedProviderMigration(request);
        if (migrationMessage != null) {
            return SpringAiModelValidationResult.failed(List.of(migrationMessage));
        }
        return registry.select(request).validate(request);
    }

    public ModelOptions buildOptions(SpringAiModelRuntimeRequest request) {
        String migrationMessage = removedProviderMigration(request);
        if (migrationMessage != null) {
            throw new IllegalArgumentException(migrationMessage);
        }
        return registry.select(request).buildOptions(request);
    }

    public SpringAiModelConnectivityResult testConnectivity(SpringAiModelRuntimeRequest request) {
        SpringAiModelValidationResult validation = validate(request);
        if (!validation.valid()) {
            return SpringAiModelConnectivityResult.failed("模型 options 校验失败：" + validation.summary());
        }
        try {
            SpringAiModelRuntimeProbe probe = new SpringAiModelRuntimeProbe(request, buildOptions(request));
            return selectProbeClient(probe).probe(probe);
        } catch (IllegalArgumentException exception) {
            return SpringAiModelConnectivityResult.failed("模型运行时配置不合法：" + exception.getMessage());
        }
    }

    private SpringAiModelProbeClient selectProbeClient(SpringAiModelRuntimeProbe probe) {
        return probeClients.stream()
                .filter(client -> client.supports(probe))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Spring AI 模型运行时暂不支持供应商类型：" + probe.request().providerKind()
                ));
    }

    private static String removedProviderMigration(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        String providerKind = request.providerKind().trim().toLowerCase(Locale.ROOT);
        if (List.of("zhipuai", "zhipu-ai", "zhipu_ai").contains(providerKind)) {
            return "Spring AI 2.0 已移除 ZhiPu AI 专用适配；请将 providerKind 迁移为 "
                    + "openai-compatible，并配置智谱 OpenAI 兼容端点、api_key 与原模型编码";
        }
        String modelType = request.modelType().trim().toUpperCase(Locale.ROOT);
        if (List.of("vertex-ai", "vertex_ai", "google-vertex-ai", "gcp_vertex_ai").contains(providerKind)
                && ("CHAT".equals(modelType) || "REASONING".equals(modelType))) {
            return "Spring AI 2.0 已移除 Vertex AI CHAT 专用适配；请将聊天模型迁移为 "
                    + "google-genai provider，Vertex AI provider 仅保留 EMBEDDING 能力";
        }
        return null;
    }

    private static <T> List<T> copyRequiredList(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return values.stream()
                .map(value -> requireElement(value, fieldName))
                .toList();
    }

    private static <T> T requireElement(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
