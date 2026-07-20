package com.aiplatform.shared.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Sha256DigestsTest {
    @Test
    void requiresLowercaseSha256AndComparesItWithoutLenientNormalization() {
        String digest = "a".repeat(64);

        assertThat(Sha256Digests.requireLowercase(digest, "options 摘要")).isEqualTo(digest);
        assertThat(Sha256Digests.constantTimeEquals(digest, digest)).isTrue();
        assertThat(Sha256Digests.constantTimeEquals(digest, "b".repeat(64))).isFalse();
        assertThat(Sha256Digests.constantTimeEquals(digest, digest.toUpperCase())).isFalse();
        assertThatThrownBy(() -> Sha256Digests.requireLowercase(digest.toUpperCase(), "options 摘要"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 位小写 SHA-256");
    }
}
