package com.aiplatform.appstudio.infrastructure.messaging.integrity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.appstudio.infrastructure.persistence.mapper.AppModelUsagePolicyPayloadDigestMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class ModelUsagePolicyPayloadIntegrityVerifierTest {
    private static final String REPORTED_DIGEST = "a".repeat(64);
    private static final String DIFFERENT_DIGEST = "b".repeat(64);

    @Test
    void acceptsOnlyWhenTheReportedDigestMatchesTheDatabaseCanonicalDigest() {
        AppModelUsagePolicyPayloadDigestMapper mapper = mock(AppModelUsagePolicyPayloadDigestMapper.class);
        when(mapper.canonicalDigest(anyString())).thenReturn(REPORTED_DIGEST);
        ModelUsagePolicyPayloadIntegrityVerifier verifier = verifier(mapper);

        assertThatCode(() -> verifier.verify(payload(REPORTED_DIGEST, "safe-model")))
                .doesNotThrowAnyException();

        verify(mapper).canonicalDigest(payload(REPORTED_DIGEST, "safe-model"));
    }

    @Test
    void rejectsAReportedDigestThatDoesNotMatchTheCanonicalPayloadWithoutEchoingEitherDigest() {
        AppModelUsagePolicyPayloadDigestMapper mapper = mock(AppModelUsagePolicyPayloadDigestMapper.class);
        when(mapper.canonicalDigest(anyString())).thenReturn(DIFFERENT_DIGEST);
        ModelUsagePolicyPayloadIntegrityVerifier verifier = verifier(mapper);

        assertThatThrownBy(() -> verifier.verify(payload(REPORTED_DIGEST, "tampered-model")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完整性")
                .hasMessageNotContaining(REPORTED_DIGEST)
                .hasMessageNotContaining(DIFFERENT_DIGEST);
    }

    @Test
    void rejectsMalformedReportedDigestBeforeCallingTheCanonicalDigestFunction() {
        AppModelUsagePolicyPayloadDigestMapper mapper = mock(AppModelUsagePolicyPayloadDigestMapper.class);
        ModelUsagePolicyPayloadIntegrityVerifier verifier = verifier(mapper);

        assertThatThrownBy(() -> verifier.verify(payload("A".repeat(64), "safe-model")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("策略摘要")
                .hasMessageNotContaining("A".repeat(64));

        verify(mapper, never()).canonicalDigest(anyString());
    }

    private static ModelUsagePolicyPayloadIntegrityVerifier verifier(
            AppModelUsagePolicyPayloadDigestMapper mapper
    ) {
        return new ModelUsagePolicyPayloadIntegrityVerifier(mapper, new JsonMapper());
    }

    private static String payload(String policyDigest, String modelCode) {
        return """
                {"modelCode":"%s","policyDigest":"%s"}
                """.formatted(modelCode, policyDigest).trim();
    }
}
