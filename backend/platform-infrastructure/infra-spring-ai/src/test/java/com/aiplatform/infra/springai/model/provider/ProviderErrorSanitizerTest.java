package com.aiplatform.infra.springai.model.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProviderErrorSanitizerTest {
    private static final String KNOWN_CREDENTIAL = "Tenant-Credential+/ secret";

    @ParameterizedTest(name = "{0}")
    @MethodSource("structuredSecretCases")
    void redactsStructuredProviderSecretsWhileKeepingSafeDiagnostics(
            String name,
            String message,
            String exposedSecret
    ) {
        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(message));

        assertThat(sanitized)
                .contains("request failed")
                .doesNotContain(exposedSecret)
                .contains("***");
    }

    private static Stream<Arguments> structuredSecretCases() {
        return Stream.of(
                Arguments.of("bearer", "request failed Authorization: Bearer bearer-secret+/=", "bearer-secret+/="),
                Arguments.of("basic", "request failed Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==", "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
                Arguments.of("query", "request failed?x_api_key=query-secret&region=cn", "query-secret"),
                Arguments.of("json", "request failed {\"client_secret\":\"json-secret\",\"code\":401}", "json-secret"),
                Arguments.of("single-quoted-json", "request failed {'access_token':'single-secret'}", "single-secret"),
                Arguments.of("private-key", "request failed -----BEGIN PRIVATE KEY-----top-secret-----END PRIVATE KEY-----", "top-secret"),
                Arguments.of("aws-access-key", "request failed AKIA1234567890ABCDEF", "1234567890ABCDEF"),
                Arguments.of("openai-key", "request failed sk-proj-provider-secret", "proj-provider-secret")
        );
    }

    @Test
    void redactsAwsAndGoogleSigningCredentialNamesWithoutDroppingStatus() {
        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed aws_secret_access_key=aws-secret"
                        + "&aws_session_token=session-secret"
                        + "&x-amz-security-token=amz-secret"
                        + "&x-goog-signature=google-secret"
                        + "&passphrase=private-passphrase&status=403"
        ));

        assertThat(sanitized)
                .contains("aws_secret_access_key=***")
                .contains("aws_session_token=***")
                .contains("x-amz-security-token=***")
                .contains("x-goog-signature=***")
                .contains("passphrase=***")
                .contains("status=403")
                .doesNotContain("aws-secret", "session-secret", "amz-secret", "google-secret", "private-passphrase");
    }

    @Test
    void redactsKnownCredentialAcrossRawPercentFormAndBase64Representations() {
        byte[] credentialBytes = KNOWN_CREDENTIAL.getBytes(StandardCharsets.UTF_8);
        String formEncoded = URLEncoder.encode(KNOWN_CREDENTIAL, StandardCharsets.UTF_8);
        String[] representations = {
                KNOWN_CREDENTIAL,
                KNOWN_CREDENTIAL.toUpperCase(),
                formEncoded,
                formEncoded.replace("+", "%20"),
                Base64.getEncoder().encodeToString(credentialBytes),
                Base64.getEncoder().withoutPadding().encodeToString(credentialBytes),
                Base64.getUrlEncoder().encodeToString(credentialBytes),
                Base64.getUrlEncoder().withoutPadding().encodeToString(credentialBytes)
        };

        for (String representation : representations) {
            String sanitized = ProviderErrorSanitizer.safeReason(
                    new IllegalStateException("request failed credential=" + representation + " endpoint=provider"),
                    KNOWN_CREDENTIAL
            );

            assertThat(sanitized)
                    .as("representation %s", representation)
                    .contains("request failed")
                    .doesNotContain(representation)
                    .doesNotContainIgnoringCase(KNOWN_CREDENTIAL)
                    .contains("***");
        }
    }

    @Test
    void decodesNestedPercentEncodingAtTheFixedDepthBeforeRedaction() {
        String nested = "Authorization%253A%2520Bearer%2520sk-deep-secret";

        String sanitized = ProviderErrorSanitizer.safeReason(
                new IllegalStateException("request failed " + nested)
        );

        assertThat(sanitized)
                .contains("request failed")
                .doesNotContain("sk-deep-secret")
                .doesNotContain("%25")
                .contains("Bearer ***");
    }

    @Test
    void redactsTheWholeQueryValueWhenTheKeyAndWhitespaceArePercentEncoded() {
        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed?api%5Fkey%3Dopen%20sesame&access%5Ftoken%3Dform+secret&status=401"
        ));

        assertThat(sanitized)
                .contains("request failed?api_key=***")
                .contains("access_token=***")
                .contains("status=401")
                .doesNotContain("open", "sesame", "form", "secret");
    }

    @Test
    void toleratesMalformedPercentAndFailsClosedWithoutSkippingLaterSecrets() {
        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed malformed=%ZZ&api%5Fkey%3Dsk-malformed-secret&region=cn"
        ));

        assertThat(sanitized)
                .contains("request failed")
                .contains("region=cn")
                .doesNotContain("%ZZ")
                .doesNotContain("sk-malformed-secret")
                .contains("***");
    }

    @Test
    void malformedPercentDoesNotConsumeTheBeginningOfTheNextCredentialKey() {
        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed malformed=%&api_key=later-secret&status=401"
        ));

        assertThat(sanitized)
                .contains("status=401")
                .doesNotContain("later-secret")
                .contains("api_key=***");
    }

    @Test
    void rejectsInvalidUtf8PercentRunsAndContinuesSanitizing() {
        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed invalid=%C3%28&token=token-secret&status=401"
        ));

        assertThat(sanitized)
                .contains("request failed")
                .contains("status=401")
                .doesNotContain("%C3%28")
                .doesNotContain("token-secret")
                .contains("***");
    }

    @Test
    void redactsSensitiveContentInsideStrictUtf8Base64Payloads() {
        String payload = Base64.getEncoder().encodeToString(
                "{\"api_key\":\"base64-secret\"}".getBytes(StandardCharsets.UTF_8)
        );

        String sanitized = ProviderErrorSanitizer.safeReason(
                new IllegalStateException("request failed payload=" + payload + " retryable=true")
        );

        assertThat(sanitized)
                .contains("request failed")
                .contains("retryable=true")
                .doesNotContain(payload)
                .doesNotContain("base64-secret")
                .contains("***");
    }

    @Test
    void redactsStandardShortAndNestedTransportEncodedBase64Payloads() {
        String standardWithPlus = Base64.getEncoder().encodeToString(
                "{\"token\":\"¾\"}".getBytes(StandardCharsets.UTF_8)
        );
        String shortAssignment = Base64.getEncoder().encodeToString(
                "token=x".getBytes(StandardCharsets.UTF_8)
        );
        String nestedPercentAuthorization = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "Authorization%3A%20Bearer%20nested-secret".getBytes(StandardCharsets.UTF_8)
        );

        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed standard=" + standardWithPlus
                        + " short=" + shortAssignment
                        + " nested=" + nestedPercentAuthorization
                        + " diagnostic=retained"
        ));

        assertThat(standardWithPlus).contains("+");
        assertThat(sanitized)
                .contains("diagnostic=retained")
                .doesNotContain(
                        standardWithPlus,
                        shortAssignment,
                        nestedPercentAuthorization,
                        "nested-secret",
                        "¾"
                );
    }

    @Test
    void failsClosedForBase64ThatDecodesToInvalidUtf8() {
        String invalidUtf8 = Base64.getEncoder().encodeToString(new byte[] {
                (byte) 0xC3, 0x28,
                (byte) 0xC3, 0x28,
                (byte) 0xC3, 0x28,
                (byte) 0xC3
        });

        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed payload=" + invalidUtf8 + " status=502"
        ));

        assertThat(sanitized)
                .contains("payload=***", "status=502")
                .doesNotContain(invalidUtf8);
    }

    @Test
    void keepsSafeProviderEndpointDiagnosticsThatAreNotPlausibleBase64() {
        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(
                "request failed endpoint=https://provider.example/models status=503"
        ));

        assertThat(sanitized)
                .contains("https://provider.example/models")
                .contains("status=503");
    }

    @Test
    void failsClosedForOversizedOpaqueEncodedTokensBeforeApplyingDiagnosticLimit() {
        String oversizedToken = "A".repeat(5000);

        String sanitized = ProviderErrorSanitizer.safeReason(
                new IllegalStateException("request failed payload=" + oversizedToken + " trailing-safe-detail")
        );

        assertThat(sanitized)
                .hasSizeLessThanOrEqualTo(180)
                .doesNotContain(oversizedToken.substring(0, 32))
                .contains("***");
    }

    @Test
    void failsClosedWhenPercentEncodingStillRemainsBeyondTheDecodeDepth() {
        String encoded = "api_key=depth-secret";
        for (int index = 0; index < 3; index++) {
            encoded = URLEncoder.encode(encoded, StandardCharsets.UTF_8);
        }

        String sanitized = ProviderErrorSanitizer.safeReason(
                new IllegalStateException("request failed payload=" + encoded + " status=401")
        );

        assertThat(sanitized)
                .contains("request failed")
                .contains("status=401")
                .doesNotContain("depth-secret")
                .doesNotContain("%")
                .contains("***");
    }

    @Test
    void sanitizesBeforeTruncatingAndDoesNotSplitSurrogatePairs() {
        String message = "request failed authorization=top-secret " + "诊断😀".repeat(100);

        String sanitized = ProviderErrorSanitizer.safeReason(new IllegalStateException(message));

        assertThat(sanitized)
                .hasSizeLessThanOrEqualTo(180)
                .doesNotContain("top-secret");
        assertThat(Character.isHighSurrogate(sanitized.charAt(sanitized.length() - 1))).isFalse();
    }

    @Test
    void fallsBackToExceptionTypeWhenMessageIsBlank() {
        assertThat(ProviderErrorSanitizer.safeReason(new IllegalStateException("  ")))
                .isEqualTo("IllegalStateException");
    }
}
