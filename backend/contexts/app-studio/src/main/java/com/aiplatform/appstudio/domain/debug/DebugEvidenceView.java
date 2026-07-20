package com.aiplatform.appstudio.domain.debug;

import java.util.Locale;

public enum DebugEvidenceView {
    SUMMARY,
    FULL;

    public static DebugEvidenceView from(String value) {
        if (value == null || value.isBlank()) {
            return SUMMARY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("调试证据 view 仅支持 summary 或 full");
        }
    }
}
