package com.aiplatform.identity.domain.permissionrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PermissionRequestSensitiveTextPolicyTest {
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {
            "password=hunter2",
            "{\"client_secret\":\"very-secret-value\"}",
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature",
            "Basic dXNlcjpwYXNzd29yZA==",
            "X-API-Key: super-secret-value",
            "AWS_SECRET_ACCESS_KEY=super-secret-value",
            "refresh_token = short-token",
            "sk-1234567890abcdef",
            "ghp_1234567890abcdef",
            "-----BEGIN PRIVATE KEY-----",
            "Authorization%3A+Bearer+eyJhbGciOiJIUzI1NiJ9.payload.signature",
            "%7B%22password%22%3A%22hunter2%22%7D",
            "api%255Fkey%253Dsk-test-secret-value",
            "api%5Fkey%3Dsk-test-secret-value%ZZ",
            "YXBpX2tleT1zay10ZXN0LXNlY3JldC12YWx1ZQ==",
            "YXBpX2tleT1zay10ZXN0LXNlY3JldC12YWx1ZQ",
            "cHdkPXg="
    })
    void rejectsSuspiciousNewReasonOrCommentInsteadOfRewritingIt(String text) {
        assertThatThrownBy(() -> PermissionRequestSensitiveTextPolicy.requireSafeNewText(text, "申请原因"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("申请原因不能包含疑似认证凭据或秘密");
    }

    @Test
    void rejectsCombinedBase64AndPercentEncodingWithinBoundedDecodeChain() {
        String encoded = Base64.getEncoder().encodeToString(
                "api%5Fkey%3Dcombined-secret".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> PermissionRequestSensitiveTextPolicy.requireSafeNewText(encoded, "处理意见"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("处理意见不能包含疑似认证凭据或秘密");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "需要完成合同审核，关联工单 123e4567-e89b-12d3-a456-426614174000",
            "采用 token bucket 算法限制流量",
            "AI application resource 3001",
            "批准临时访问，截止 2026-07-31",
            "批准进入下一次不可变发布；活动 v1 不得原地扩权"
    })
    void keepsOrdinaryBusinessTextUnchanged(String text) {
        assertThat(PermissionRequestSensitiveTextPolicy.requireSafeNewText(text, "申请原因"))
                .isEqualTo(text);
        assertThat(PermissionRequestSensitiveTextPolicy.sanitizeHistoricalText(text))
                .isEqualTo(text);
    }

    @Test
    void quarantinesLegacyReasonAndCommentWithoutLeakingPartialEncodedMaterial() {
        PermissionRequest legacy = new PermissionRequest(
                TenantId.of("2001"),
                "request-legacy",
                "1001",
                "AI_APPLICATION",
                "3001",
                "app-studio:application:use",
                "历史原因 api_key=legacy-secret",
                PermissionRequestStatus.REJECTED,
                "1002",
                "历史意见 eyJwYXNzd29yZCI6Imh1bnRlcjIifQ==",
                NOW,
                NOW
        );

        PermissionRequest safe = PermissionRequestSensitiveTextPolicy.sanitizeHistorical(legacy);

        assertThat(safe.reason()).isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        assertThat(safe.decisionComment()).isEqualTo(PermissionRequestSensitiveTextPolicy.REDACTED);
        assertThat(safe.toString())
                .doesNotContain("legacy-secret")
                .doesNotContain("eyJwYXNzd29yZCI6Imh1bnRlcjIifQ==");
    }
}
