package com.aiplatform.foundation.provider.ollama;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelType;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OllamaModelFactoryTest {

    @Test
    void 对话与嵌入工厂都必须执行统一出站策略() {
        var policyCalls = new AtomicInteger();
        var policy = new com.aiplatform.foundation.security.egress.OutboundEndpointPolicy() {
            @Override
            public void verify(URI endpoint) {
                policyCalls.incrementAndGet();
            }
        };

        assertDoesNotThrow(() -> new OllamaChatModelFactory(policy)
                .create(specification(ModelType.CHAT)));
        assertDoesNotThrow(() -> new OllamaEmbeddingModelFactory(policy)
                .create(specification(ModelType.EMBEDDING)));
        assertEquals(2, policyCalls.get());
    }

    @Test
    void 工厂不得接受不匹配的模型类型() {
        var factory = new OllamaChatModelFactory(endpoint -> {
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(specification(ModelType.EMBEDDING)));
    }

    private static ModelRuntimeSpecification specification(ModelType type) {
        return new ModelRuntimeSpecification(
                new TenantId(100000000000000001L),
                310000000000000001L,
                2,
                ProviderKind.OLLAMA,
                URI.create("http://ollama.internal:11434"),
                new SecretReference("secret://environment/OLLAMA_UNUSED"),
                320000000000000001L,
                3,
                type == ModelType.CHAT ? "qwen-chat" : "nomic-embed",
                type,
                8192,
                type == ModelType.CHAT ? 1024 : null,
                type == ModelType.EMBEDDING ? 768 : null);
    }
}
