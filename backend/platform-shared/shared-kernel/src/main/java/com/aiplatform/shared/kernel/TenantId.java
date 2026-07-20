package com.aiplatform.shared.kernel;

public final class TenantId implements ValueObject {
    private final String value;

    public TenantId(String value) {
        this.value = requireText(value, "租户 ID");
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TenantId tenantId)) {
            return false;
        }
        return value.equals(tenantId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
