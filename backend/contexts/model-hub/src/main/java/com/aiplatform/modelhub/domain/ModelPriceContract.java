package com.aiplatform.modelhub.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Exact domain mirror of PostgreSQL NUMERIC(18,8) used by model pricing columns. */
public final class ModelPriceContract {
    private static final int MAX_INTEGER_DIGITS = 10;
    private static final int MAX_FRACTION_DIGITS = 8;

    private ModelPriceContract() {
    }

    public static BigDecimal requireDatabaseValue(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为空");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于 0");
        }
        final BigDecimal normalized;
        try {
            normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + "必须符合 NUMERIC(18,8)，整数最多 10 位且小数最多 8 位", exception);
        }
        long scale = normalized.scale();
        long integerDigits = Math.max((long) normalized.precision() - scale, 0L);
        if (scale > MAX_FRACTION_DIGITS || integerDigits > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException(fieldName + "必须符合 NUMERIC(18,8)，整数最多 10 位且小数最多 8 位");
        }
        return normalized;
    }

    public static String canonicalDatabaseString(BigDecimal value, String fieldName) {
        return requireDatabaseValue(value, fieldName).toPlainString();
    }
}
