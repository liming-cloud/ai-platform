package com.aiplatform.modelhub.application.idempotency;

import java.util.regex.Pattern;

/** Public request-id grammar shared by provider and model creation commands. */
public final class ModelCreationRequestId {
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private ModelCreationRequestId() {
    }

    public static String require(String value) {
        if (value == null || !ALLOWED.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException("模型创建 requestId 必须为 1 到 128 位安全字符");
        }
        return value.trim();
    }
}
