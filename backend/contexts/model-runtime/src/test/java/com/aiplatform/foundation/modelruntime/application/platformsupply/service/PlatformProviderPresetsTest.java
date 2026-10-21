package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformProviderPresetsTest {
    @Test
    void 仅硅基流动重排预设显式选择官方列表且旧版本不被静默升级() {
        var rerank = PlatformProviderPresets.view().items().stream()
                .filter(preset -> preset.id().equals("SILICONFLOW")).findFirst().orElseThrow()
                .variants().stream().filter(variant -> variant.capability().equals("RERANK")).findFirst().orElseThrow();
        assertEquals("SILICONFLOW_RERANK_MODELS_API", rerank.discoveryProfile());
        assertEquals("VENDOR_POST_PROCESSOR", rerank.runtimeProtocol());
        assertNotEquals("2026-10-12", PlatformProviderPresets.VERSION);
        for (String id : List.of("CUSTOM", "ALIYUN")) {
            var other = PlatformProviderPresets.view().items().stream().filter(preset -> preset.id().equals(id))
                    .findFirst().orElseThrow().variants().stream()
                    .filter(variant -> variant.capability().equals("RERANK")).findFirst().orElseThrow();
            assertEquals("NONE", other.discoveryProfile());
        }
    }

    @Test
    void 五厂商全部显式能力组合按目录校验且不进行网络或秘密读取() {
        int checked = 0;
        for (var preset : PlatformProviderPresets.view().items()) {
            for (var variant : preset.variants()) {
                var workspace = variant.workspaceRequired() ? "synthetic-workspace-1" : null;
                var address = variant.endpointTemplate().isEmpty() ? "https://models.example.invalid/v1"
                        : variant.endpointTemplate().replace("{workspaceId}", workspace == null ? "" : workspace);
                var selection = new PlatformProviderPresetSelection(preset.id(), PlatformProviderPresets.VERSION,
                        variant.capability(), workspace);
                assertDoesNotThrow(() -> PlatformProviderPresets.validate(selection, ProviderKind.OPENAI_COMPATIBLE,
                        PlatformModelDiscoveryProfile.valueOf(variant.discoveryProfile()), URI.create(address), preset.region()));
                checked++;
            }
        }
        assertEquals(11, checked);
    }

    @Test
    void 伪造过期来源能力协议区域或固定地址均拒绝() {
        var endpoint = URI.create("https://api.deepseek.com");
        for (var selection : java.util.List.of(
                new PlatformProviderPresetSelection("UNKNOWN", PlatformProviderPresets.VERSION, "CHAT", null),
                new PlatformProviderPresetSelection("DEEPSEEK", "2026-01-01", "CHAT", null),
                new PlatformProviderPresetSelection("DEEPSEEK", PlatformProviderPresets.VERSION, "EMBEDDING", null),
                new PlatformProviderPresetSelection("DEEPSEEK", PlatformProviderPresets.VERSION, "CHAT", "extra"))) {
            assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(selection,
                    ProviderKind.OPENAI_COMPATIBLE, PlatformModelDiscoveryProfile.OPENAI_MODELS_API, endpoint, "provider-default"));
        }
        var deepseek = new PlatformProviderPresetSelection("DEEPSEEK", PlatformProviderPresets.VERSION, "CHAT", null);
        assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(deepseek,
                ProviderKind.DETERMINISTIC, PlatformModelDiscoveryProfile.OPENAI_MODELS_API, endpoint, "provider-default"));
        assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(deepseek,
                ProviderKind.OPENAI_COMPATIBLE, PlatformModelDiscoveryProfile.NONE, endpoint, "provider-default"));
        assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(deepseek,
                ProviderKind.OPENAI_COMPATIBLE, PlatformModelDiscoveryProfile.OPENAI_MODELS_API, endpoint, "cn-beijing"));
        for (String address : java.util.List.of("https://api.deepseek.com.evil.invalid", "https://api.deepseek.com/",
                "http://api.deepseek.com", "https://api.deepseek.com:8443", "https://other.example.invalid/v1")) {
            assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(deepseek,
                    ProviderKind.OPENAI_COMPATIBLE, PlatformModelDiscoveryProfile.OPENAI_MODELS_API, URI.create(address), "provider-default"));
        }
    }

    @Test
    void 业务空间只接受完整DNS标识且Embedding不能携带空间() {
        for (String workspace : java.util.List.of("", "UPPER", "../evil", "a.evil.invalid", "x@evil", "-x", "x-", "a".repeat(64))) {
            assertThrows(IllegalArgumentException.class, () -> new PlatformProviderPresetSelection(
                    "ALIYUN", PlatformProviderPresets.VERSION, "CHAT", workspace));
        }
        var chat = new PlatformProviderPresetSelection("ALIYUN", PlatformProviderPresets.VERSION, "CHAT", "workspace-1");
        assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(chat,
                ProviderKind.OPENAI_COMPATIBLE, PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                URI.create("https://workspace-2.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"), "cn-beijing"));
        var embedding = new PlatformProviderPresetSelection("ALIYUN", PlatformProviderPresets.VERSION, "EMBEDDING", "workspace-1");
        assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(embedding,
                ProviderKind.OPENAI_COMPATIBLE, PlatformModelDiscoveryProfile.OPENAI_MODELS_API,
                URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1"), "cn-beijing"));
    }

    @Test
    void 自定义地址拒绝用户信息查询片段与非HTTPS且能力仍只是意图() {
        var custom = new PlatformProviderPresetSelection("CUSTOM", PlatformProviderPresets.VERSION, "CHAT", null);
        for (String address : java.util.List.of("http://models.example.invalid/v1", "https://user@models.example.invalid/v1",
                "https://models.example.invalid/v1?secret=synthetic", "https://models.example.invalid/v1#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(custom,
                    ProviderKind.OPENAI_COMPATIBLE, PlatformModelDiscoveryProfile.OPENAI_MODELS_API, URI.create(address), "unspecified"));
        }
        var rerank = new PlatformProviderPresetSelection("SILICONFLOW", PlatformProviderPresets.VERSION, "RERANK", null);
        assertDoesNotThrow(() -> PlatformProviderPresets.validate(rerank, ProviderKind.OPENAI_COMPATIBLE,
                PlatformModelDiscoveryProfile.SILICONFLOW_RERANK_MODELS_API, URI.create("https://api.siliconflow.cn/v1/rerank"), "cn"));
        assertThrows(IllegalArgumentException.class, () -> PlatformProviderPresets.validate(rerank, ProviderKind.OPENAI_COMPATIBLE,
                PlatformModelDiscoveryProfile.OPENAI_MODELS_API, URI.create("https://api.siliconflow.cn/v1/rerank"), "cn"));
    }
}
