package com.aiplatform.modelhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModelSecretRefTest {
    @Test
    void trimsExplicitSecretReferenceAndMaskedValue() {
        ModelSecretRef secretRef = new ModelSecretRef(" 9001 ", " sk-***tail ");

        assertThat(secretRef.secretId()).isEqualTo("9001");
        assertThat(secretRef.maskedValue()).isEqualTo("sk-***tail");
    }

    @Test
    void rejectsBlankMaskedValueInsteadOfPlaceholderCompatibility() {
        assertThatThrownBy(() -> new ModelSecretRef("9001", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("凭证材料脱敏值不能为空");
    }
}
