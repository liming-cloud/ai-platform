package com.aiplatform.foundation.assistantdelivery.application.answer.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelInvocationTraceTest {

    @Test
    void 应按冻结顺序保留每次候选的最小结果事实() {
        var trace = new ModelInvocationTrace(List.of(
                new ModelInvocationTrace.Attempt(1, 40, 4, ModelInvocationTrace.Outcome.TRANSIENT_FAILURE),
                new ModelInvocationTrace.Attempt(2, 41, 5, ModelInvocationTrace.Outcome.SUCCEEDED)));

        assertEquals(2, trace.attempts().size());
        assertEquals(40, trace.attempts().getFirst().configurationId());
        assertEquals(ModelInvocationTrace.Outcome.SUCCEEDED, trace.attempts().getLast().outcome());
    }

    @Test
    void 轨迹必须连续且仅允许最后一次成功() {
        assertThrows(IllegalArgumentException.class, () -> new ModelInvocationTrace(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ModelInvocationTrace(List.of(
                new ModelInvocationTrace.Attempt(2, 41, 5, ModelInvocationTrace.Outcome.SUCCEEDED))));
        assertThrows(IllegalArgumentException.class, () -> new ModelInvocationTrace(List.of(
                new ModelInvocationTrace.Attempt(1, 41, 5, ModelInvocationTrace.Outcome.SUCCEEDED),
                new ModelInvocationTrace.Attempt(2, 42, 5, ModelInvocationTrace.Outcome.TRANSIENT_FAILURE))));
    }
}
