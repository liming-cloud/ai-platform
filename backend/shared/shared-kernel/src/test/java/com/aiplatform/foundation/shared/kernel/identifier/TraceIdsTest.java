package com.aiplatform.foundation.shared.kernel.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TraceIdsTest {

    @Test
    void 应使用雪花标识生成服务端追踪标识() {
        assertEquals(
                "trc_540000000000000001",
                TraceIds.next(() -> 540000000000000001L));
    }
}
