package com.aiplatform.foundation.assistantdelivery.application.answer.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModelInvocationTraceTest {

    @Test
    void 应仅保留命中候选与尝试次数() {
        var trace = new ModelInvocationTrace(2, 41, 5);

        assertEquals(2, trace.attempts());
        assertEquals(41, trace.selectedConfigurationId());
        assertEquals(5, trace.selectedConfigurationRevision());
    }

    @Test
    void 尝试次数和冻结候选必须有效() {
        assertThrows(IllegalArgumentException.class, () -> new ModelInvocationTrace(0, 41, 5));
        assertThrows(IllegalArgumentException.class, () -> new ModelInvocationTrace(1, 0, 5));
    }
}
