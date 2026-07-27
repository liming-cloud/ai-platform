package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.common.ModelType;
import com.aiplatform.foundation.modelruntime.api.model.provider.ProviderKind;
import com.aiplatform.foundation.security.secret.SecretReference;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleModelFactoryTest {

    private static final SecretReference SECRET_REFERENCE =
            new SecretReference("secret://environment/OPENAI_API_KEY");

    @Test
    void 对话工厂应先校验端点并在构造后关闭短期密钥() {
        var policyCalls = new AtomicInteger();
        var secret = SecretValue.copyOf("test-secret".toCharArray());
        var factory = new OpenAiCompatibleChatModelFactory(
                reference -> secret,
                endpoint -> policyCalls.incrementAndGet(),
                Duration.ofSeconds(10));

        assertDoesNotThrow(() -> factory.create(chatSpecification()));
        assertEquals(1, policyCalls.get());
        assertThrows(IllegalStateException.class, () -> secret.use(chars -> null));
    }

    @Test
    void 嵌入工厂不得接受对话部署() {
        var factory = new OpenAiCompatibleEmbeddingModelFactory(
                reference -> SecretValue.copyOf("test-secret".toCharArray()),
                endpoint -> {
                },
                Duration.ofSeconds(10));

        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(chatSpecification()));
    }

    private static ModelRuntimeSpecification chatSpecification() {
        return new ModelRuntimeSpecification(
                new TenantId(100000000000000001L),
                310000000000000001L,
                2,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://models.example.com/v1"),
                SECRET_REFERENCE,
                320000000000000001L,
                3,
                "gpt-compatible",
                ModelType.CHAT,
                8192,
                1024,
                null);
    }
}
