package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.preset.PlatformProviderPresetsView;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.preset.PlatformProviderPresetsView.Preset;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.preset.PlatformProviderPresetsView.Variant;
import java.util.List;
import java.net.URI;
import java.util.Objects;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDiscoveryProfile;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind;

/** 承接已接受原型的五厂商预设，版本不表示厂商真实探测已完成。 */
public final class PlatformProviderPresets {
    public static final String VERSION = "2026-10-12";
    private static final PlatformProviderPresetsView VIEW = new PlatformProviderPresetsView(VERSION, List.of(
            preset("CUSTOM", "自定义连接", "unspecified", "自行配置", List.of(
                    variant("CHAT", "", false), variant("EMBEDDING", "", false), variant("RERANK", "", false))),
            preset("DEEPSEEK", "DeepSeek", "provider-default", "厂商默认", List.of(
                    variant("CHAT", "https://api.deepseek.com", false))),
            preset("ALIYUN", "阿里云百炼", "cn-beijing", "中国北京", List.of(
                    variant("CHAT", "https://{workspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", true),
                    variant("EMBEDDING", "https://dashscope.aliyuncs.com/compatible-mode/v1", false),
                    variant("RERANK", "https://{workspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank", true))),
            preset("SILICONFLOW", "硅基流动", "cn", "中国区", List.of(
                    variant("CHAT", "https://api.siliconflow.cn/v1", false),
                    variant("EMBEDDING", "https://api.siliconflow.cn/v1", false),
                    variant("RERANK", "https://api.siliconflow.cn/v1/rerank", false))),
            new Preset("VOLCENGINE", "火山方舟", "cn-beijing", "中国北京", List.of("CHAT"),
                    "VOLCENGINE_ARK_CONTROL_PLANE", List.of(new Variant("CHAT",
                    "https://ark.cn-beijing.volces.com/api/v3", false, "OPENAI_COMPATIBLE", "VOLCENGINE_ARK_CONTROL_PLANE")))));

    private PlatformProviderPresets() {}

    /** 返回不可变非秘密目录，查询不访问网络、连接存储或凭据。 */
    public static PlatformProviderPresetsView view() { return VIEW; }

    /**
     * 校验浏览器提交的选择与受控目录一致；实际网络调用仍逐次执行出站策略。
     * @throws IllegalArgumentException 版本、能力、发现协议、区域或固定地址不匹配时拒绝。
     */
    public static void validate(PlatformProviderPresetSelection selection, ProviderKind kind,
            PlatformModelDiscoveryProfile discovery, URI endpoint, String region) {
        Objects.requireNonNull(selection, "厂商预设来源不能为空");
        if (!VERSION.equals(selection.version())) throw new IllegalArgumentException("厂商预设版本已变化，请重新加载");
        var preset = VIEW.items().stream().filter(item -> item.id().equals(selection.providerId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("厂商预设不存在"));
        var variant = preset.variants().stream().filter(item -> item.capability().equals(selection.capability())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("厂商预设不支持所选能力"));
        if (kind != ProviderKind.OPENAI_COMPATIBLE || !variant.discoveryProfile().equals(discovery.name())
                || !preset.region().equals(region)) {
            throw new IllegalArgumentException("厂商预设与运行协议、发现协议或区域不一致");
        }
        if (variant.workspaceRequired() != (selection.workspaceId() != null)) {
            throw new IllegalArgumentException("所选能力的业务空间配置不正确");
        }
        Objects.requireNonNull(endpoint, "运行地址不能为空");
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("运行地址必须是无用户信息、查询或片段的 HTTPS 地址");
        }
        if (!variant.endpointTemplate().isEmpty()) {
            var expected = variant.endpointTemplate().replace("{workspaceId}",
                    selection.workspaceId() == null ? "" : selection.workspaceId());
            if (!expected.equals(endpoint.toString())) throw new IllegalArgumentException("运行地址与所选厂商预设不一致");
        }
    }

    private static Preset preset(String id, String name, String region, String regionLabel, List<Variant> variants) {
        return new Preset(id, name, region, regionLabel, variants.stream().map(Variant::capability).toList(),
                "OPENAI_MODELS_API", variants);
    }

    private static Variant variant(String capability, String endpoint, boolean workspace) {
        var rerank = capability.equals("RERANK");
        return new Variant(capability, endpoint, workspace, rerank ? "VENDOR_POST_PROCESSOR" : "OPENAI_COMPATIBLE",
                rerank ? "NONE" : "OPENAI_MODELS_API");
    }
}
