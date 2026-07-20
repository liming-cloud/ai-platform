package com.aiplatform.modelhub.infrastructure.springai.adapter;

import java.util.Map;

/** Canonical runtime options reconstructed from the current persisted model configuration. */
record RuntimeOptionsAuthority(
        String providerKind,
        Map<String, Object> effectiveOptions,
        String effectiveOptionsDigest
) {
}
