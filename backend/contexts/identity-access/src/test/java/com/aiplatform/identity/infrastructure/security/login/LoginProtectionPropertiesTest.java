package com.aiplatform.identity.infrastructure.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginProtectionPropertiesTest {
    @Test
    void usesSecureDefaultsAndAcceptsBoundedConfiguration() {
        LoginProtectionProperties properties = new LoginProtectionProperties();

        assertThat(properties.getMaxFailures()).isEqualTo(5);
        assertThat(properties.getWindow()).isEqualTo(Duration.ofMinutes(15));

        properties.setMaxFailures(8);
        properties.setWindow(Duration.ofMinutes(30));
        assertThat(properties.getMaxFailures()).isEqualTo(8);
        assertThat(properties.getWindow()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rejectsUnsafeThresholdAndWindowConfiguration() {
        LoginProtectionProperties properties = new LoginProtectionProperties();

        assertThatThrownBy(() -> properties.setMaxFailures(2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3 到 20");
        assertThatThrownBy(() -> properties.setMaxFailures(21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3 到 20");
        assertThatThrownBy(() -> properties.setWindow(Duration.ofSeconds(59)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 分钟到 24 小时");
        assertThatThrownBy(() -> properties.setWindow(Duration.ofHours(25)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 分钟到 24 小时");
    }
}
