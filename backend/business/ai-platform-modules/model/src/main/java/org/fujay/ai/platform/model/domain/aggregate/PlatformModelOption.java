package org.fujay.ai.platform.model.domain.aggregate;

import org.fujay.ai.platform.model.domain.value.ModelCapabilityType;
import org.fujay.ai.platform.model.domain.value.ModelParameterDefaults;
import org.fujay.ai.platform.model.domain.value.ProviderType;
import org.fujay.ai.platform.shared.ddd.AggregateRoot;

public record PlatformModelOption(
        Long id,
        Long providerTypeId,
        String modelCode,
        String displayName,
        ModelCapabilityType capabilityType,
        ModelParameterDefaults defaults,
        String status,
        Integer sortOrder) implements AggregateRoot {

    public PlatformModelOption {
        requireId(id, "模型选项标识不能为空");
        requireId(providerTypeId, "供应商类型标识不能为空");
        requireNotBlank(modelCode, "模型编码不能为空");
        requireNotBlank(displayName, "模型显示名称不能为空");
        if (capabilityType == null) {
            throw new IllegalArgumentException("模型能力类型不能为空");
        }
        defaults = defaults == null
                ? new ModelParameterDefaults(null, null, null, null, null, null, null, null, null)
                : defaults;
        requireNotBlank(status, "模型选项状态不能为空");
        sortOrder = sortOrder == null ? 100 : sortOrder;
    }

    public void validateFor(ProviderType providerType) {
        defaults.validate(providerType, capabilityType);
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
