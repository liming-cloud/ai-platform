package com.aiplatform.appstudio.domain.port.chat.model;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.Objects;

/**
 * AI 应用模型运行时连接引用，统一承载供应商地址、鉴权类型和凭证引用。
 */
public record AppModelRuntimeConnectionRef(
        String endpoint,
        String authType,
        String secretRefId
) implements ValueObject {
    public AppModelRuntimeConnectionRef {
        endpoint = requireText(endpoint, "模型运行时 endpoint");
        authType = requireText(authType, "模型运行时 authType");
        secretRefId = normalizeOptionalText(secretRefId);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
