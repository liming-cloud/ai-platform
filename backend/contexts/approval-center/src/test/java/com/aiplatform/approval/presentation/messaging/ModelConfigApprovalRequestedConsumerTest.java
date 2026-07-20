package com.aiplatform.approval.presentation.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aiplatform.approval.domain.model.ApprovalCancellationRequest;
import com.aiplatform.approval.domain.model.ApprovalRequest;
import com.aiplatform.approval.domain.model.ApprovalRiskLevel;
import com.aiplatform.approval.domain.port.ApprovalPort;
import com.aiplatform.approval.presentation.messaging.consumer.ModelConfigApprovalRequestedConsumer;
import com.aiplatform.infra.rocketmq.model.DomainEventMessage;
import com.aiplatform.shared.kernel.ApprovalEvidenceEnvelope;
import com.aiplatform.shared.kernel.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ModelConfigApprovalRequestedConsumerTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-05-25T08:00:00Z");
    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void convertsCurrentModelConfigApprovalEventToEvidenceBoundRequest() throws Exception {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
        Payload payload = configPayload("MODEL_CONFIG_CREATE", null,
                "openai-compatible", "openai-compatible", "https://api.example.com/v1");

        consumer.onMessage(message("model-hub", "v2", "ModelConfig", "4001", payload.json()));

        ArgumentCaptor<ApprovalRequest> requestCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalPort).requestApproval(requestCaptor.capture());
        ApprovalRequest request = requestCaptor.getValue();
        assertThat(request.tenantId().value()).isEqualTo("2001");
        assertThat(request.approvalRequestId()).isEqualTo("REQ-1001");
        assertThat(request.approvalType()).isEqualTo("model_config_change");
        assertThat(request.sourceContext()).isEqualTo("model-hub");
        assertThat(request.sourceResourceType()).isEqualTo("model_config");
        assertThat(request.sourceResourceId()).isEqualTo("4001");
        assertThat(request.title()).isEqualTo("创建模型配置 GPT 4.1 Mini");
        assertThat(request.riskLevel()).isEqualTo(ApprovalRiskLevel.HIGH_RISK);
        assertThat(request.requesterId()).isEqualTo("1001");
        assertThat(request.traceId()).isEqualTo("TRACE-EVENT-001");
        assertThat(request.evidence().digest()).isEqualTo(payload.evidenceDigest());
        assertThat(request.targetDigest()).isEqualTo(payload.targetDigest());
    }

    @Test
    void acceptsUpdateEvidenceWhoseExactBeforeConnectivityWasPassedOrFailed() throws Exception {
        for (String connectivityStatus : List.of("PASSED", "FAILED")) {
            ApprovalPort approvalPort = mock(ApprovalPort.class);
            ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
            Payload payload = configPayload("MODEL_CONFIG_UPDATE", connectivityStatus,
                    "openai-compatible", "openai-compatible", "https://api.example.com/v1");

            consumer.onMessage(message("model-hub", "v2", "ModelConfig", "4001", payload.json()));

            verify(approvalPort).requestApproval(org.mockito.ArgumentMatchers.argThat(request ->
                    request.evidence().digest().equals(payload.evidenceDigest())
            ));
        }
    }

    @Test
    void acceptsCanonicalMicrosecondEvidenceAfterPostgresOccurredAtRoundTrip() throws Exception {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
        Instant sourceClock = Instant.parse("2026-05-25T08:00:00.123456789Z");
        Payload payload = configPayload(
                "MODEL_CONFIG_CREATE",
                null,
                "openai-compatible",
                "openai-compatible",
                "https://api.example.com/v1",
                sourceClock.truncatedTo(ChronoUnit.MICROS)
        );

        consumer.onMessage(message(
                "model-hub",
                "v2",
                "ModelConfig",
                "4001",
                payload.json(),
                sourceClock
        ));

        verify(approvalPort).requestApproval(org.mockito.ArgumentMatchers.argThat(request ->
                request.evidence().facts().get("requestedAt").equals("2026-05-25T08:00:00.123456Z")
        ));
    }

    @Test
    void acceptsCanonicalBedrockTruncateStringEvidence() throws Exception {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
        Payload payload = configPayload(
                "MODEL_CONFIG_CREATE",
                null,
                "aws_bedrock",
                "bedrock",
                "https://bedrock-runtime.us-east-1.amazonaws.com",
                Map.of(
                        "providerKind", "bedrock",
                        "region", "us-east-1",
                        "timeoutSeconds", new BigDecimal("15"),
                        "embeddingProvider", "cohere",
                        "inputType", "SEARCH_DOCUMENT",
                        "truncate", "END"
                )
        );

        consumer.onMessage(message("model-hub", "v2", "ModelConfig", "4001", payload.json()));

        verify(approvalPort).requestApproval(org.mockito.ArgumentMatchers.argThat(request -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> target = (Map<String, Object>) request.evidence().facts().get("target");
            @SuppressWarnings("unchecked")
            Map<String, Object> options = (Map<String, Object>) target.get("runtimeOptions");
            return "END".equals(options.get("truncate")) && "bedrock".equals(options.get("providerKind"));
        }));
    }

    @Test
    void delayedV1RequestIsAcknowledgedWithoutRecreatingGhostTask() throws Exception {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);

        consumer.onMessage(message("model-hub", "v1", "ModelConfig", "4001", "{}"));

        verifyNoInteractions(approvalPort);
    }

    @Test
    void convertsCanonicalMicrosecondCancellationAfterPostgresOutboxRoundTrip() {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
        Instant sourceClock = Instant.parse("2026-05-25T08:00:00.123456789Z");
        Instant persistedAt = sourceClock.truncatedTo(ChronoUnit.MICROS);

        consumer.onMessage(cancellationMessage(
                "model-hub",
                "ModelProvider",
                "3001",
                persistedAt,
                """
                        {
                          "approvalRequestId":"REQ-1001",
                          "sourceContext":"model-hub",
                          "sourceResourceType":"model_provider",
                          "sourceResourceId":"3001",
                          "operatorId":"1002",
                          "reason":"供应商地址录入错误",
                          "requestedAt":"2026-05-25T08:00:00.123456Z"
                        }
                        """
        ));

        ArgumentCaptor<ApprovalCancellationRequest> cancellationCaptor =
                ArgumentCaptor.forClass(ApprovalCancellationRequest.class);
        verify(approvalPort).cancelApproval(cancellationCaptor.capture());
        assertThat(cancellationCaptor.getValue()).isEqualTo(new ApprovalCancellationRequest(
                TenantId.of("2001"),
                "ModelApprovalCancellationRequested:2001:model_provider:REQ-1001",
                "REQ-1001",
                "model-hub",
                "model_provider",
                "3001",
                "1002",
                "供应商地址录入错误",
                "TRACE-EVENT-001",
                persistedAt
        ));
    }

    @Test
    void rejectsNanosecondPayloadThatDoesNotMatchPersistedMicrosecondEnvelope() {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
        Instant sourceClock = Instant.parse("2026-05-25T08:00:00.123456789Z");
        Instant persistedAt = sourceClock.truncatedTo(ChronoUnit.MICROS);
        String payload = """
                {
                  "approvalRequestId":"REQ-1001",
                  "sourceContext":"model-hub",
                  "sourceResourceType":"model_provider",
                  "sourceResourceId":"3001",
                  "operatorId":"1002",
                  "reason":"供应商地址录入错误",
                  "requestedAt":"2026-05-25T08:00:00.123456789Z"
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(cancellationMessage(
                "model-hub", "ModelProvider", "3001", persistedAt, payload
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型审批撤销事件 envelope 与载荷不一致");
        verifyNoInteractions(approvalPort);
    }

    @Test
    void rejectsForgedOrInconsistentCancellationBeforePersistence() {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
        String payload = """
                {
                  "approvalRequestId":"REQ-1001",
                  "sourceContext":"model-hub",
                  "sourceResourceType":"model_provider",
                  "sourceResourceId":"3001",
                  "operatorId":"1002",
                  "reason":"供应商地址录入错误",
                  "requestedAt":"2026-05-25T08:00:00Z"
                }
                """;

        assertThatThrownBy(() -> consumer.onMessage(cancellationMessage(
                "untrusted-producer", "ModelProvider", "3001", OCCURRED_AT, payload
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型审批撤销事件 envelope 与载荷不一致");
        assertThatThrownBy(() -> consumer.onMessage(cancellationMessage(
                "model-hub", "ModelConfig", "3001", OCCURRED_AT, payload
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型审批撤销事件 envelope 与载荷不一致");
        assertThatThrownBy(() -> consumer.onMessage(cancellationMessage(
                "model-hub", "ModelProvider", "3001", OCCURRED_AT.plusSeconds(1), payload
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型审批撤销事件 envelope 与载荷不一致");
        verifyNoInteractions(approvalPort);
    }

    @Test
    void rejectsInconsistentEnvelopeAndPayloadFactsBeforeCreatingAnyTask() throws Exception {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);
        String payload = configPayload("MODEL_CONFIG_CREATE", null,
                "openai-compatible", "openai-compatible", "https://api.example.com/v1").json();

        assertRejected(consumer, message("untrusted-producer", "v2", "ModelConfig", "4001", payload));
        assertRejected(consumer, message("model-hub", "v3", "ModelConfig", "4001", payload));
        assertRejected(consumer, message("model-hub", "v2", "ModelProvider", "4001", payload));
        assertRejected(consumer, message("model-hub", "v2", "ModelConfig", "4999", payload));

        verifyNoInteractions(approvalPort);
    }

    @Test
    void rejectsSelfConsistentProviderKindAndEndpointForgeries() throws Exception {
        ApprovalPort approvalPort = mock(ApprovalPort.class);
        ModelConfigApprovalRequestedConsumer consumer = consumer(approvalPort);

        Payload mismatchedKind = configPayload("MODEL_CONFIG_CREATE", null,
                "openai-compatible", "bedrock", "https://api.example.com/v1");
        Payload unsafeEndpoint = configPayload("MODEL_CONFIG_CREATE", null,
                "openai-compatible", "openai-compatible", "https://user:secret@api.example.com/v1");

        assertThatThrownBy(() -> consumer.onMessage(message(
                "model-hub", "v2", "ModelConfig", "4001", mismatchedKind.json()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerCode 与 providerKind");
        assertThatThrownBy(() -> consumer.onMessage(message(
                "model-hub", "v2", "ModelConfig", "4001", unsafeEndpoint.json()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS/443");
        verifyNoInteractions(approvalPort);
    }

    private static ModelConfigApprovalRequestedConsumer consumer(ApprovalPort approvalPort) {
        return new ModelConfigApprovalRequestedConsumer(approvalPort, JSON);
    }

    private static void assertRejected(ModelConfigApprovalRequestedConsumer consumer, DomainEventMessage message) {
        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("模型配置审批事件 envelope 与载荷不一致");
    }

    private static DomainEventMessage message(
            String producer,
            String schemaVersion,
            String aggregateType,
            String aggregateId,
            String payload
    ) {
        return message(producer, schemaVersion, aggregateType, aggregateId, payload, OCCURRED_AT);
    }

    private static DomainEventMessage message(
            String producer,
            String schemaVersion,
            String aggregateType,
            String aggregateId,
            String payload,
            Instant occurredAt
    ) {
        return new DomainEventMessage(
                "ModelConfigApprovalRequested:2001:model_config:REQ-1001",
                "ModelConfigApprovalRequested",
                "2001",
                aggregateType,
                aggregateId,
                1,
                schemaVersion,
                occurredAt,
                "TRACE-EVENT-001",
                producer,
                payload
        );
    }

    private static DomainEventMessage cancellationMessage(
            String producer,
            String aggregateType,
            String aggregateId,
            Instant occurredAt,
            String payload
    ) {
        return new DomainEventMessage(
                "ModelApprovalCancellationRequested:2001:model_provider:REQ-1001",
                "ModelApprovalCancellationRequested",
                "2001",
                aggregateType,
                aggregateId,
                1,
                "v1",
                occurredAt,
                "TRACE-EVENT-001",
                producer,
                payload
        );
    }

    private static Payload configPayload(
            String action,
            String beforeConnectivityStatus,
            String providerCode,
            String providerKind,
            String endpoint
    ) throws Exception {
        return configPayload(action, beforeConnectivityStatus, providerCode, providerKind, endpoint, OCCURRED_AT);
    }

    private static Payload configPayload(
            String action,
            String beforeConnectivityStatus,
            String providerCode,
            String providerKind,
            String endpoint,
            Instant requestedAt
    ) throws Exception {
        return configPayload(action, beforeConnectivityStatus, providerCode, providerKind, endpoint, requestedAt, Map.of(
                "providerKind", providerKind,
                "temperature", new BigDecimal("0.2")
        ));
    }

    private static Payload configPayload(
            String action,
            String beforeConnectivityStatus,
            String providerCode,
            String providerKind,
            String endpoint,
            Map<String, Object> runtimeOptions
    ) throws Exception {
        return configPayload(action, beforeConnectivityStatus, providerCode, providerKind, endpoint, OCCURRED_AT,
                runtimeOptions);
    }

    private static Payload configPayload(
            String action,
            String beforeConnectivityStatus,
            String providerCode,
            String providerKind,
            String endpoint,
            Instant requestedAt,
            Map<String, Object> runtimeOptions
    ) throws Exception {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("providerId", "3001");
        provider.put("providerCode", providerCode);
        provider.put("providerKind", providerKind);
        provider.put("providerName", "Enterprise provider");
        provider.put("endpoint", endpoint);

        Map<String, Object> target = configSnapshot("GPT 4.1 Mini", runtimeOptions,
                "INACTIVE", "NOT_TESTED", null, "");
        Map<String, Object> after = new LinkedHashMap<>(target);
        after.put("provider", provider);

        Map<String, Object> before = null;
        if (beforeConnectivityStatus != null) {
            before = configSnapshot("GPT 4.1 Legacy", runtimeOptions,
                    "ACTIVE", beforeConnectivityStatus, "2026-05-25T07:30:00Z", "probe result");
            before.put("provider", provider);
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("tenantId", "2001");
        facts.put("approvalRequestId", "REQ-1001");
        facts.put("approvalType", "model_config_change");
        facts.put("action", action);
        facts.put("sourceContext", "model-hub");
        facts.put("resourceType", "model_config");
        facts.put("resourceId", "4001");
        facts.put("providerId", "3001");
        facts.put("modelConfigId", "4001");
        facts.put("requesterId", "1001");
        facts.put("requestedAt", requestedAt.toString());
        facts.put("riskLevel", "HIGH_RISK");
        facts.put("expectedPostDecisionState", "INACTIVE+NOT_TESTED");
        facts.put("before", before);
        facts.put("after", after);
        facts.put("target", target);
        facts.put("changedFields", changedFields(before, after));
        ApprovalEvidenceEnvelope evidence = ApprovalEvidenceEnvelope.create(facts);
        String targetDigest = ApprovalEvidenceEnvelope.digestFacts(Map.of("target", target));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalRequestId", "REQ-1001");
        payload.put("approvalType", "model_config_change");
        payload.put("providerId", "3001");
        payload.put("modelConfigId", "4001");
        payload.put("sourceResourceType", "model_config");
        payload.put("sourceResourceId", "4001");
        payload.put("changeSummary", "创建模型配置 GPT 4.1 Mini");
        payload.put("riskLevel", "HIGH_RISK");
        payload.put("operatorId", "1001");
        payload.put("evidenceSchema", evidence.schema());
        payload.put("evidenceDigest", evidence.digest());
        payload.put("targetDigest", targetDigest);
        payload.put("evidence", evidence.facts());
        return new Payload(JSON.writeValueAsString(payload), evidence.digest(), targetDigest);
    }

    private static Map<String, Object> configSnapshot(
            String modelName,
            Map<String, Object> runtimeOptions,
            String expectedStatus,
            String connectivityStatus,
            String connectivityCheckedAt,
            String connectivityMessage
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelConfigId", "4001");
        snapshot.put("modelCode", "gpt-4.1-mini");
        snapshot.put("modelName", modelName);
        snapshot.put("modelType", "CHAT");
        snapshot.put("contextWindow", 128_000);
        snapshot.put("embeddingDimension", null);
        snapshot.put("inputPricePer1k", "0.0004");
        snapshot.put("outputPricePer1k", "0.0016");
        snapshot.put("runtimeOptions", runtimeOptions);
        snapshot.put("expectedStatus", expectedStatus);
        snapshot.put("connectivityStatus", connectivityStatus);
        snapshot.put("connectivityCheckedAt", connectivityCheckedAt);
        snapshot.put("connectivityMessage", connectivityMessage);
        return snapshot;
    }

    private static List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        if (before == null) {
            return new TreeSet<>(after.keySet()).stream().toList();
        }
        Set<String> changed = new TreeSet<>();
        collectChanged("", before, after, changed);
        return changed.stream().toList();
    }

    private static void collectChanged(
            String prefix,
            Map<String, Object> before,
            Map<String, Object> after,
            Set<String> changed
    ) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object left = before.get(key);
            Object right = after.get(key);
            if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
                collectChanged(path, stringMap(leftMap), stringMap(rightMap), changed);
            } else if (!java.util.Objects.equals(left, right)) {
                changed.add(path);
            }
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private record Payload(String json, String evidenceDigest, String targetDigest) {
    }
}
