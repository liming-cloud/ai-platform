package com.aiplatform.foundation.provider.openaicompatible;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelRuntimeSpecification;
import com.aiplatform.foundation.modelruntime.api.model.ModelType;
import com.aiplatform.foundation.modelruntime.api.model.ProviderKind;
import com.aiplatform.foundation.security.secret.SecretReference;
import com.aiplatform.foundation.security.secret.SecretValue;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
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
                new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                2,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://models.example.com/v1"),
                SECRET_REFERENCE,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                3,
                "gpt-compatible",
                ModelType.CHAT,
                8192,
                1024,
                null);
    }
}
