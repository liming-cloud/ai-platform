package com.aiplatform.shared.kernel;

import java.math.BigDecimal;

/** Canonical decimal value and its bounded non-exponent textual representation. */
public record CanonicalDecimal(BigDecimal value, String plainText) {
}
