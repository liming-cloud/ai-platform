package com.aiplatform.identity.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class LoginCommandTest {
    @Test
    void trimsTenantAndLoginNameButPreservesUsernameCaseAndRawPassword() {
        LoginCommand command = new LoginCommand(
                TenantId.of(" 2001 "),
                "  AdMiN  ",
                " password-with-significant-spaces ",
                " TRACE-LOGIN-NORMALIZE "
        );

        assertThat(command.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(command.loginName()).isEqualTo("AdMiN");
        assertThat(command.rawPassword()).isEqualTo(" password-with-significant-spaces ");
        assertThat(command.traceId()).isEqualTo("TRACE-LOGIN-NORMALIZE");
    }
}
