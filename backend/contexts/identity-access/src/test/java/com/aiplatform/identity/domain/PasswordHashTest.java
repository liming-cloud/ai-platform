package com.aiplatform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordHashTest {
    @Test
    void acceptsSupportedPbkdf2HashFormat() {
        PasswordHash hash = PasswordHash.of("pbkdf2-sha256$1000$c2FsdA==$aGFzaA==");

        assertThat(hash.value()).isEqualTo("pbkdf2-sha256$1000$c2FsdA==$aGFzaA==");
    }

    @Test
    void rejectsUnsupportedOrMalformedHashFormat() {
        assertThatThrownBy(() -> PasswordHash.of("bcrypt$1000$c2FsdA==$aGFzaA=="))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordHash.of("pbkdf2-sha256$not-number$c2FsdA==$aGFzaA=="))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordHash.of("pbkdf2-sha256$1000$not-base64$aGFzaA=="))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordHash.of("pbkdf2-sha256$1000$c2FsdA==$"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingHashValueInsteadOfJdkNullPointerException() {
        assertThatThrownBy(() -> PasswordHash.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码哈希不能为空");
        assertThatThrownBy(() -> PasswordHash.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码哈希不能为空白");
    }
}
