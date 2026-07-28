package com.aiplatform.foundation.modelruntime.domain.configuration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ControlledModelCatalogEntryTest {

    @Test
    void 受控目录条目必须由平台发布后才能供模型配置选择() {
        var entry = ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://ark.cn-beijing.volces.com/api/v3"),
                "test-chat-model",
                ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096),
                Instant.parse("2026-07-28T00:00:00Z"));

        assertEquals(ControlledModelCatalogEntryStatus.DRAFT, entry.status());
        assertEquals(ControlledModelCatalogEntryStatus.PUBLISHED, entry.publish().status());
        assertEquals(32_768, entry.publish().capabilities().contextWindow());
        assertEquals(4_096, entry.publish().capabilities().outputLimit());
    }

    @Test
    void 目录端点禁止携带用户信息查询或片段() {
        assertThrows(IllegalArgumentException.class, () -> ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://user@example.com/path?token=forbidden"),
                "test-chat-model",
                ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096),
                Instant.parse("2026-07-28T00:00:00Z")));
    }

    @Test
    void 目录端点必须使用HTTPS() {
        assertThrows(IllegalArgumentException.class, () -> ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://example.com/compatible-mode/v1"),
                "test-chat-model",
                ModelType.CHAT,
                ModelRuntimeCapabilities.chat(32_768, 4_096),
                Instant.parse("2026-07-28T00:00:00Z")));
    }

    @Test
    void 目录能力快照必须与模型类型一致() {
        assertThrows(IllegalArgumentException.class, () -> ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://example.com/compatible-mode/v1"),
                "test-embedding-model",
                ModelType.EMBEDDING,
                ModelRuntimeCapabilities.chat(32_768, 4_096),
                Instant.parse("2026-07-28T00:00:00Z")));
    }
}
