package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ModelPriceContractTest {
    @Test
    void acceptsExactNumeric18Scale8Boundaries() {
        assertThat(ModelPriceContract.requireDatabaseValue(
                new BigDecimal("9999999999.99999999"),
                "输入单价"
        )).isEqualByComparingTo("9999999999.99999999");
        assertThat(ModelPriceContract.requireDatabaseValue(
                new BigDecimal("0.00000001"),
                "输出单价"
        )).isEqualByComparingTo("0.00000001");
    }

    @Test
    void rejectsValuesPostgresWouldRoundOrOverflowBeforeEvidenceDigesting() {
        assertThatThrownBy(() -> ModelPriceContract.requireDatabaseValue(
                new BigDecimal("0.123456789"),
                "输入单价"
        )).hasMessageContaining("NUMERIC(18,8)");
        assertThatThrownBy(() -> ModelPriceContract.requireDatabaseValue(
                new BigDecimal("10000000000.00000000"),
                "输出单价"
        )).hasMessageContaining("NUMERIC(18,8)");
    }

    @Test
    void rejectsExtremeExponentsWithoutIntegerOverflowOrLargeAllocation() {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThatThrownBy(() -> ModelPriceContract.requireDatabaseValue(
                    new BigDecimal("1E+2147483647"),
                    "输入单价"
            )).hasMessageContaining("NUMERIC(18,8)");
            assertThatThrownBy(() -> ModelPriceContract.requireDatabaseValue(
                    new BigDecimal("1E-2147483647"),
                    "输出单价"
            )).hasMessageContaining("NUMERIC(18,8)");
        });
    }
}
