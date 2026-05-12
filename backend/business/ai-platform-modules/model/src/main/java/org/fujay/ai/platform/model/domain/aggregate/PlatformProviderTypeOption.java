package org.fujay.ai.platform.model.domain.aggregate;

import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.ddd.AggregateRoot;

public record PlatformProviderTypeOption(
        Long id,
        ProviderType providerType,
        String displayName,
        String defaultBaseUrl,
        String status,
        Integer sortOrder) implements AggregateRoot {

    public PlatformProviderTypeOption {
        requireId(id, "供应商类型标识不能为空");
        if (providerType == null) {
            throw new IllegalArgumentException("供应商类型不能为空");
        }
        requireNotBlank(displayName, "供应商类型名称不能为空");
        requireNotBlank(defaultBaseUrl, "供应商默认 baseUrl 不能为空");
        requireNotBlank(status, "供应商类型状态不能为空");
        sortOrder = sortOrder == null ? 100 : sortOrder;
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
