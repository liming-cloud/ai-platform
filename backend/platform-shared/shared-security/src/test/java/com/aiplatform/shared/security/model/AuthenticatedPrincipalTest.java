package com.aiplatform.shared.security.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import org.junit.jupiter.api.Test;

class AuthenticatedPrincipalTest {
    @Test
    void normalizesPrincipalFactsBeforeContextUse() {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(TenantId.of("2001"), " user ", " 1001 ");

        assertThat(principal.actorType()).isEqualTo("user");
        assertThat(principal.actorId()).isEqualTo("1001");
    }

    @Test
    void rejectsBlankPrincipalFacts() {
        TenantId tenantId = TenantId.of("2001");

        assertThatThrownBy(() -> new AuthenticatedPrincipal(null, "user", "1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
        assertThatThrownBy(() -> new AuthenticatedPrincipal(tenantId, " ", "1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("主体类型不能为空");
        assertThatThrownBy(() -> new AuthenticatedPrincipal(tenantId, "user", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("主体 ID不能为空");
    }
}
