package com.aiplatform.modelhub.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class DisableModelCommandTest {
    @Test
    void normalizesBoundedReasonForAudit() {
        DisableModelProviderCommand command = new DisableModelProviderCommand(
                TenantId.of("2001"), "1001", "3001", "  供应商合同已到期  ", 0);

        assertThat(command.reason()).isEqualTo("供应商合同已到期");
    }

    @Test
    void rejectsBlankOversizedAndControlCharacterReasons() {
        assertThatThrownBy(() -> new DisableModelProviderCommand(
                TenantId.of("2001"), "1001", "3001", "  ", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DisableModelConfigCommand(
                TenantId.of("2001"), "1001", "3001", "4001", "x".repeat(501), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DisableModelConfigCommand(
                TenantId.of("2001"), "1001", "3001", "4001", "原因\u0000损坏", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DisableModelProviderCommand(
                TenantId.of("2001"), "1001", "3001", "版本无效", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
