package com.aiplatform.foundation.kernel.identifier;

import java.util.Objects;

/**
 * 生成不接受调用方输入的服务端追踪标识。
 */
public final class TraceIds {

    private static final String PREFIX = "trc_";

    private TraceIds() {
    }

    public static String next(EntityIdGenerator identifiers) {
        Objects.requireNonNull(identifiers, "标识生成器不能为空");
        return PREFIX + identifiers.nextId();
    }
}
