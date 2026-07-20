package com.aiplatform.shared.kernel;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Canonicalizes decimal values without materializing an attacker-controlled plain string first.
 */
public final class BoundedDecimalCanonicalizer {
    private BoundedDecimalCanonicalizer() {
    }

    public static CanonicalDecimal canonicalize(Number number, int maxCharacters, String fieldName) {
        Objects.requireNonNull(number, fieldName + "不能为空");
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("数字字符上限必须大于 0");
        }
        final BigDecimal decimal;
        try {
            decimal = number instanceof BigDecimal bigDecimal
                    ? bigDecimal
                    : new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + "必须是有限十进制数字", exception);
        }
        if (decimal.signum() == 0) {
            return new CanonicalDecimal(BigDecimal.ZERO, "0");
        }

        final BigDecimal normalized;
        try {
            normalized = decimal.stripTrailingZeros();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + "数字范围超过上限", exception);
        }
        long estimatedCharacters = estimatedPlainCharacters(normalized);
        if (estimatedCharacters > maxCharacters) {
            throw new IllegalArgumentException(fieldName + "数字精度超过上限");
        }
        return new CanonicalDecimal(normalized, normalized.toPlainString());
    }

    private static long estimatedPlainCharacters(BigDecimal decimal) {
        long precision = decimal.precision();
        long scale = decimal.scale();
        long unsignedCharacters;
        if (scale <= 0) {
            unsignedCharacters = precision - scale;
        } else if (precision > scale) {
            unsignedCharacters = precision + 1L;
        } else {
            unsignedCharacters = scale + 2L;
        }
        return unsignedCharacters + (decimal.signum() < 0 ? 1L : 0L);
    }
}
