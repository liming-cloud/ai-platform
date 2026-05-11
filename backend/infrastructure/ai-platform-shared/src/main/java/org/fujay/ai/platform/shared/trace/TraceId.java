package org.fujay.ai.platform.shared.trace;

import java.util.Objects;

public record TraceId(String value) {

    public TraceId {
        Objects.requireNonNull(value, "追踪标识不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException("追踪标识不能为空白");
        }
    }
}
