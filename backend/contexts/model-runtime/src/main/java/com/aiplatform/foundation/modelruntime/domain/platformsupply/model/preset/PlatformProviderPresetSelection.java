package com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 连接修订冻结的非秘密预设来源与能力意图，不代表厂商能力已验证。
 * 工作空间仅用于构造受控地址，不进入公开连接摘要。
 */
public record PlatformProviderPresetSelection(
        String providerId, String version, String capability, String workspaceId) implements ValueObject {
    /**
     * @throws IllegalArgumentException 预设、版本、能力或 DNS label 格式不正确时拒绝。
     */
    public PlatformProviderPresetSelection {
        if (providerId == null || !providerId.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("厂商预设标识格式不正确");
        }
        if (version == null || !version.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("厂商预设版本格式不正确");
        }
        if (capability == null || !java.util.Set.of("CHAT", "EMBEDDING", "RERANK").contains(capability)) {
            throw new IllegalArgumentException("能力意图不受支持");
        }
        if (workspaceId != null && !workspaceId.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new IllegalArgumentException("业务空间标识格式不正确");
        }
    }
}
