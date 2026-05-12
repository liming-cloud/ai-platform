package org.fujay.ai.platform.model.domain.service;

import java.math.BigDecimal;
import java.util.List;
import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;

public final class DefaultPlatformModelCatalog {

    private DefaultPlatformModelCatalog() {
    }

    public static List<ProviderTypeSpec> providerTypes() {
        return List.of(
                new ProviderTypeSpec(ProviderType.OPENAI, "OpenAI", "https://api.openai.com", 10),
                new ProviderTypeSpec(
                        ProviderType.OPENAI_COMPATIBLE,
                        "OpenAI 兼容供应商",
                        "https://api.siliconflow.cn",
                        20));
    }

    public static List<ModelSpec> models() {
        return List.of(
                new ModelSpec(
                        ProviderType.OPENAI,
                        "o3",
                        "o3",
                        ModelCapabilityType.CHAT,
                        new ModelParameterDefaults(200000, 1024, null, null, null, null, null, null, "medium"),
                        10),
                new ModelSpec(
                        ProviderType.OPENAI_COMPATIBLE,
                        "tencent/Hunyuan-MT-7B",
                        "Hunyuan-MT-7B 免费翻译模型",
                        ModelCapabilityType.CHAT,
                        new ModelParameterDefaults(
                                33792,
                                33792,
                                null,
                                BigDecimal.valueOf(0.2),
                                BigDecimal.valueOf(0.7),
                                null,
                                null,
                                null,
                                null),
                        20));
    }

    public record ProviderTypeSpec(
            ProviderType providerType, String displayName, String defaultBaseUrl, Integer sortOrder) {
    }

    public record ModelSpec(
            ProviderType providerType,
            String modelCode,
            String displayName,
            ModelCapabilityType capabilityType,
            ModelParameterDefaults defaults,
            Integer sortOrder) {
    }
}
