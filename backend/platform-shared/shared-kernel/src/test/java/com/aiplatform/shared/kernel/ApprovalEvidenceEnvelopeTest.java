package com.aiplatform.shared.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.stream.IntStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ApprovalEvidenceEnvelopeTest {
    @Test
    void digestIsStableAcrossMapKeyOrderAndNumericRepresentations() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("runtimeOptions", Map.of("temperature", new BigDecimal("0.700"), "stop", List.of("z", "a")));
        first.put("providerId", "provider-1");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("providerId", "provider-1");
        second.put("runtimeOptions", Map.of("stop", List.of("z", "a"), "temperature", 0.7d));

        ApprovalEvidenceEnvelope left = ApprovalEvidenceEnvelope.create(first);
        ApprovalEvidenceEnvelope right = ApprovalEvidenceEnvelope.create(second);

        assertThat(left.digest()).isEqualTo(right.digest()).hasSize(64);
        assertThat(left.canonicalFacts()).isEqualTo(right.canonicalFacts());
    }

    @Test
    void arrayOrderAndDuplicateElementsRemainCryptographicallySignificant() {
        ApprovalEvidenceEnvelope ordered = ApprovalEvidenceEnvelope.create(Map.of(
                "stop", List.of("END", "STOP")
        ));
        ApprovalEvidenceEnvelope reordered = ApprovalEvidenceEnvelope.create(Map.of(
                "stop", List.of("STOP", "END")
        ));
        ApprovalEvidenceEnvelope repeated = ApprovalEvidenceEnvelope.create(Map.of(
                "stop", List.of("END", "END", "STOP")
        ));

        assertThat(reordered.digest()).isNotEqualTo(ordered.digest());
        assertThat(repeated.digest()).isNotEqualTo(ordered.digest());
    }

    @Test
    void nullAndAbsentFactsRemainCryptographicallyDistinct() {
        Map<String, Object> nullable = new LinkedHashMap<>();
        nullable.put("value", null);
        ApprovalEvidenceEnvelope withNull = ApprovalEvidenceEnvelope.create(Map.of("before", nullable));
        ApprovalEvidenceEnvelope absent = ApprovalEvidenceEnvelope.create(Map.of("before", Map.of()));

        assertThat(withNull.digest()).isNotEqualTo(absent.digest());
    }

    @Test
    void constructorRejectsTamperedDigestAndUnknownSchema() {
        ApprovalEvidenceEnvelope valid = ApprovalEvidenceEnvelope.create(Map.of("providerId", "provider-1"));

        assertThatThrownBy(() -> new ApprovalEvidenceEnvelope(
                valid.schema(),
                "0".repeat(64),
                valid.facts()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("摘要");
        assertThatThrownBy(() -> new ApprovalEvidenceEnvelope(
                "model-approval-evidence/v0",
                valid.digest(),
                valid.facts()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema");
    }

    @Test
    void factsAreDeeplyImmutableAndRejectNonJsonValues() {
        ApprovalEvidenceEnvelope evidence = ApprovalEvidenceEnvelope.create(Map.of(
                "after",
                Map.of("changedFields", List.of("modelName"))
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) evidence.facts().get("after");
        assertThatThrownBy(() -> after.put("modelName", "tampered"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of("bad", new Object())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of("bad", Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数字");
    }

    @Test
    void acceptsDocumentedDepthCollectionAndStringBoundaries() {
        Map<String, Object> exactMapWidth = new LinkedHashMap<>();
        IntStream.range(0, 128).forEach(index -> exactMapWidth.put("k" + index, index));
        List<String> exactListWidth = IntStream.range(0, 128).mapToObj(String::valueOf).toList();

        assertThat(ApprovalEvidenceEnvelope.create(nested(8)).digest()).hasSize(64);
        assertThat(ApprovalEvidenceEnvelope.create(exactMapWidth).digest()).hasSize(64);
        assertThat(ApprovalEvidenceEnvelope.create(Map.of("items", exactListWidth)).digest()).hasSize(64);
        assertThat(ApprovalEvidenceEnvelope.create(Map.of("text", "x".repeat(8_192))).digest()).hasSize(64);
    }

    @Test
    void rejectsDepthNodeWidthStringNumberAndCanonicalSizeOverflowBeforeUse() {
        List<Map<String, Object>> nodeOverflow = IntStream.range(0, 128)
                .mapToObj(index -> Map.<String, Object>of(
                        "a", 1, "b", 2, "c", 3, "d", 4,
                        "e", 5, "f", 6, "g", 7, "h", 8
                ))
                .toList();
        Map<String, Object> mapOverflow = new LinkedHashMap<>();
        IntStream.range(0, 129).forEach(index -> mapOverflow.put("k" + index, index));

        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(nested(9)))
                .hasMessageContaining("深度");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of("nodes", nodeOverflow)))
                .hasMessageContaining("节点");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(mapOverflow))
                .hasMessageContaining("字段数量");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of(
                "items", IntStream.range(0, 129).boxed().toList()
        ))).hasMessageContaining("条目数量");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of("text", "x".repeat(8_193))))
                .hasMessageContaining("长度");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of(
                "number", new BigDecimal("9".repeat(129))
        ))).hasMessageContaining("数字精度");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of(
                "large", IntStream.range(0, 128).mapToObj(index -> "x".repeat(1_020)).toList()
        ))).hasMessageContaining("canonical");
    }

    @Test
    void rejectsExtremeDecimalExponentsBeforeAllocatingPlainStrings() {
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of(
                    "number", new BigDecimal("1E+100000000")
            ))).hasMessageContaining("数字精度");
            assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of(
                    "number", new BigDecimal("1E-2147483647")
            ))).hasMessageContaining("数字精度");
        });
    }

    @Test
    void rejectsRawEncodedEscapedAndNestedBase64CredentialMaterial() {
        String assignment = "api_key=super-secret-value-123456";
        String encoded = Base64.getEncoder().encodeToString(assignment.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String nested = Base64.getUrlEncoder().withoutPadding().encodeToString(
                encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of("name", assignment)))
                .hasMessageContaining("疑似包含凭证");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of(
                "endpoint",
                "https://api.example.com/%2561%2570%2569%255f%256b%2565%2579%253dsecret-value-123456"
        ))).hasMessageContaining("疑似包含凭证");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of(
                "message",
                "api_key\\u003dsecret-value-123456"
        ))).hasMessageContaining("疑似包含凭证");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of("message", encoded)))
                .hasMessageContaining("疑似包含凭证");
        assertThatThrownBy(() -> ApprovalEvidenceEnvelope.create(Map.of("message", nested)))
                .hasMessageContaining("疑似包含凭证");
    }

    @Test
    void sharedSecretPolicyDetectsKnownCredentialRepresentationsWithoutRejectingSafeDiagnostics() {
        String credential = "arbitrary-runtime-credential-987654";
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                credential.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> ApprovalEvidenceSecretPolicy.requireSecretFree(
                "Provider " + encoded,
                "模型供应商名称",
                credential
        )).hasMessageContaining("疑似包含凭证");
        assertThatThrownBy(() -> ApprovalEvidenceSecretPolicy.requireSecretFree(
                "Provider " + credential,
                "模型供应商名称",
                " " + credential + " "
        )).hasMessageContaining("疑似包含凭证");
        String awsCredentialJson = """
                {"accessKeyId":"AKIAEXAMPLE00000000","secretAccessKey":"opaque-secret-value-123456"}
                """;
        String encodedLeaf = Base64.getEncoder().encodeToString(
                "opaque-secret-value-123456".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        assertThatThrownBy(() -> ApprovalEvidenceSecretPolicy.requireSecretFree(
                "Provider opaque-secret-value-123456",
                "模型供应商名称",
                awsCredentialJson
        )).hasMessageContaining("疑似包含凭证");
        assertThatThrownBy(() -> ApprovalEvidenceSecretPolicy.requireSecretFree(
                "Provider " + encodedLeaf,
                "模型供应商名称",
                awsCredentialJson
        )).hasMessageContaining("疑似包含凭证");
        String escapedKeyCredentialJson = "{\"secret\\u0041ccessKey\":\"opaque-value-from-escaped-key\"}";
        assertThatThrownBy(() -> ApprovalEvidenceSecretPolicy.requireSecretFree(
                "Provider opaque-value-from-escaped-key",
                "模型供应商名称",
                escapedKeyCredentialJson
        )).hasMessageContaining("疑似包含凭证");
        assertThat(ApprovalEvidenceSecretPolicy.requireSecretFree(
                "https://api.example.com/v1/models",
                "模型供应商访问地址"
        )).isEqualTo("https://api.example.com/v1/models");
        assertThat(ApprovalEvidenceSecretPolicy.requireSecretFree(
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                "模型供应商访问地址"
        )).isEqualTo("https://bedrock-runtime.us-east-1.amazonaws.com");
        assertThat(ApprovalEvidenceSecretPolicy.requireSecretFree(
                "Connection timed out after 10 seconds",
                "连通性说明"
        )).isEqualTo("Connection timed out after 10 seconds");
    }

    private static Map<String, Object> nested(int depth) {
        Object value = "leaf";
        for (int index = 0; index < depth; index++) {
            value = Map.of("level" + index, value);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) value;
        return nested;
    }
}
