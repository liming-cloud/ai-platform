package com.aiplatform.foundation.controlplane.web;

import java.util.UUID;

/**
 * 生成不包含调用方输入的服务端追踪标识。
 */
public final class TraceIds {

    private TraceIds() {
    }

    public static String next() {
        return "trc_" + UUID.randomUUID();
    }
}
