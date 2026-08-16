package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OperationsRatesTest {

    @Test
    void 零受理量必须返回零比率而不是非数字() {
        var rates = OperationsRates.from(0L, 0L, 0L, 0L);

        assertEquals(0.0d, rates.answerRate());
        assertEquals(0.0d, rates.refusalRate());
        assertEquals(0.0d, rates.failureRate());
    }

    @Test
    void 比率必须以受理量为分母并保留四位小数() {
        var rates = OperationsRates.from(3L, 1L, 1L, 1L);

        assertEquals(0.3333d, rates.answerRate());
        assertEquals(0.3333d, rates.refusalRate());
        assertEquals(0.3333d, rates.failureRate());
    }
}
