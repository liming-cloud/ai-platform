package com.aiplatform.infra.satoken.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SaTokenPrincipalCodecTest {
    @Test
    void decodesValidLoginId() {
        assertThat(SaTokenPrincipalCodec.decode("2001:user:1001"))
                .hasValueSatisfying(principal -> {
                    assertThat(principal.tenantId().value()).isEqualTo("2001");
                    assertThat(principal.actorType()).isEqualTo("user");
                    assertThat(principal.actorId()).isEqualTo("1001");
                });
    }

    @Test
    void normalizesLoginIdFacts() {
        assertThat(SaTokenPrincipalCodec.requireLoginId(" 2001 : user : 1001 "))
                .isEqualTo("2001:user:1001");
        assertThat(SaTokenPrincipalCodec.userLoginId(com.aiplatform.shared.kernel.TenantId.of("2001"), " 1001 "))
                .isEqualTo("2001:user:1001");
    }

    @Test
    void rejectsMissingTenantIdInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> SaTokenPrincipalCodec.userLoginId(null, "1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("租户 ID不能为空");
    }

    @Test
    void keepsMissingLoginIdAsAnonymous() {
        assertThat(SaTokenPrincipalCodec.decode(null)).isEmpty();
    }

    @Test
    void rejectsMalformedLoginIdInsteadOfEmptyPrincipalFallback() {
        assertThatThrownBy(() -> SaTokenPrincipalCodec.decode("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体格式非法");
        assertThatThrownBy(() -> SaTokenPrincipalCodec.decode(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体不能为空白");
    }

    @Test
    void rejectsNonStringLoginIdInsteadOfToStringCoercion() {
        Object objectLoginId = new StringBuilder("2001:user:1001");

        assertThatThrownBy(() -> SaTokenPrincipalCodec.requireLoginId(objectLoginId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体类型非法");
        assertThatThrownBy(() -> SaTokenPrincipalCodec.decode(objectLoginId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体类型非法");
    }
}
