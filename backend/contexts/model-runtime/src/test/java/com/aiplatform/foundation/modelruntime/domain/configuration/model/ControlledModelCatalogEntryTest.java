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
                URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1"),
                "qwen-plus",
                ModelType.CHAT,
                Instant.parse("2026-07-28T00:00:00Z"));

        assertEquals(ControlledModelCatalogEntryStatus.DRAFT, entry.status());
        assertEquals(ControlledModelCatalogEntryStatus.PUBLISHED, entry.publish().status());
    }

    @Test
    void 目录端点禁止携带用户信息查询或片段() {
        assertThrows(IllegalArgumentException.class, () -> ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://user@example.com/path?token=forbidden"),
                "qwen-plus",
                ModelType.CHAT,
                Instant.parse("2026-07-28T00:00:00Z")));
    }

    @Test
    void 目录端点必须使用HTTPS() {
        assertThrows(IllegalArgumentException.class, () -> ControlledModelCatalogEntry.create(
                new ControlledModelCatalogEntryId(3001L),
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://example.com/compatible-mode/v1"),
                "qwen-plus",
                ModelType.CHAT,
                Instant.parse("2026-07-28T00:00:00Z")));
    }
}
