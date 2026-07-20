package com.aiplatform.modelhub.domain;

import com.aiplatform.shared.kernel.ValueObject;

/**
 * 模型供应商凭证材料引用值对象，领域层只保存引用和脱敏值，不保存明文凭证。
 */
public record ModelSecretRef(String secretId, String maskedValue) implements ValueObject {
    public ModelSecretRef {
        secretId = requireText(secretId, "凭证材料引用 ID");
        maskedValue = requireText(maskedValue, "凭证材料脱敏值");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
