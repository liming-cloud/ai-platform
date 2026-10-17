package com.aiplatform.foundation.modelruntime.application.platformsupply.dto.preset;

import java.util.List;

/** 平台批准的非秘密预设；公开地址模板不来自任何已保存连接。 */
public record PlatformProviderPresetsView(String version, List<Preset> items, boolean writesAvailable) {
    public PlatformProviderPresetsView { items = List.copyOf(items); }

    /** 厂商和能力仅表示连接意图，不能代替真实模型能力验证。 */
    public record Preset(String id, String name, String region, String regionLabel,
            List<String> capabilities, String discoveryProfile, List<Variant> variants) {
        public Preset { capabilities = List.copyOf(capabilities); variants = List.copyOf(variants); }
    }

    /** 地址联动只使用受控模板；业务空间只能替换明确的占位符。 */
    public record Variant(String capability, String endpointTemplate, boolean workspaceRequired,
            String runtimeProtocol, String discoveryProfile) {}
}
