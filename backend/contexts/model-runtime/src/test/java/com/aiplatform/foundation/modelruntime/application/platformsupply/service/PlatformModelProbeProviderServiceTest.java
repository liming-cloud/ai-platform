package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult;
import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformCapabilitySampleRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationPrice;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilitySampleExecutor;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformReranker;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.*;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.preset.PlatformProviderPresetSelection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult;
import com.aiplatform.foundation.modelruntime.domain.provider.model.CredentialReference;
import com.aiplatform.foundation.security.identity.*;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import java.net.URI;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class PlatformModelProbeProviderServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-21T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AtomicLong identifiers = new AtomicLong(500);
    private final AtomicBoolean session = new AtomicBoolean(true);
    private final RecordingStore store = new RecordingStore();

    @Test void 三类分别走自己的端口并在第一请求前预留完整计划() {
        for (var type : ModelType.values()) {
            var adapter = new Samples(); var d = draft(type);
            var service = service(adapter, List.of(price(type)));
            var result = service.probe(command(), d, connection(type));
            assertTrue(result.succeeded(), type + "/" + result.code()); assertEquals(PlatformModelDeploymentStatus.VERIFIED, result.deployment().status());
            assertEquals(Set.of(ModelCapability.valueOf(type.name())), result.observed());
            assertEquals(3, adapter.calls.size());
            assertEquals(3, adapter.calls.stream().map(PlatformCapabilitySampleRequest::binding).distinct().count());
            assertEquals(type == ModelType.RERANK ? 3 : 0, adapter.rerankCalls);
            assertEquals(2L, adapter.calls.getFirst().connectionRevision());
            assertTrue(adapter.calls.stream().allMatch(call -> call.timeout().compareTo(Duration.ofSeconds(30)) <= 0));
            assertEquals(3, store.lastPassed.size()); assertEquals("PASSED", store.lastOutcome);
        }
    }

    @Test void 缺失过期或不匹配的账户报价均不预留也不外呼() {
        var valid = price(ModelType.CHAT);
        var expired = new PlatformCapabilityVerificationPrice(valid.connectionId(), valid.connectionRevision(), valid.modelCode(),
                valid.modelType(), valid.residency(), valid.evidenceDigest(), NOW.minusSeconds(120), NOW.minusSeconds(1),
                "CNY", valid.billingUnit(), valid.inputPrice(), valid.outputPrice(), 1024, 1024);
        var other = new PlatformCapabilityVerificationPrice(11, 1, valid.modelCode(), valid.modelType(), valid.residency(),
                valid.evidenceDigest(), valid.observedAt(), valid.expiresAt(), "CNY", valid.billingUnit(), valid.inputPrice(), valid.outputPrice(), 1024, 1024);
        for (var prices : List.of(List.<PlatformCapabilityVerificationPrice>of(), List.of(expired), List.of(other))) {
            var adapter = new Samples();
            var result = service(adapter, prices).probe(command(), draft(ModelType.CHAT), connection(ModelType.CHAT));
            assertFalse(result.succeeded()); assertEquals("S2_PRICING_UNAVAILABLE", result.code());
            assertTrue(adapter.calls.isEmpty()); assertEquals(0, store.reservations.get());
        }
    }

    @Test void 三样本费用不能完整预留时第一请求也不发送() {
        var valid = price(ModelType.CHAT);
        var expensive = new PlatformCapabilityVerificationPrice(11, 2, valid.modelCode(), valid.modelType(), valid.residency(),
                valid.evidenceDigest(), valid.observedAt(), valid.expiresAt(), "CNY", "PER_REQUEST",
                new BigDecimal("0.02"), BigDecimal.ZERO, 1, 1024);
        var adapter = new Samples();
        var result = service(adapter, List.of(expensive)).probe(command(), draft(ModelType.CHAT), connection(ModelType.CHAT));
        assertEquals("S2_BUDGET_EXCEEDED", result.code()); assertTrue(adapter.calls.isEmpty()); assertEquals(0, store.reservations.get());
    }

    @Test void 缺少独立重排适配不能借Chat成功() {
        var adapter = new Samples();
        var service = new PlatformModelProbeProviderService(List.of(adapter), List.of(), List.of(price(ModelType.RERANK)), store,
                CLOCK, identifiers::incrementAndGet, Duration.ofHours(24));
        var result = service.probe(command(), draft(ModelType.RERANK), connection(ModelType.RERANK));
        assertEquals("S2_PROTOCOL_UNAVAILABLE", result.code()); assertTrue(adapter.calls.isEmpty()); assertEquals(0, store.reservations.get());
    }

    @Test void 缺少用量或错误正文立即停止且不能验证通过() {
        for (boolean missingUsage : List.of(true, false)) {
            var adapter = new Samples(); adapter.missingUsage = missingUsage; adapter.badText = !missingUsage;
            var result = service(adapter, List.of(price(ModelType.CHAT))).probe(command(), draft(ModelType.CHAT), connection(ModelType.CHAT));
            assertFalse(result.succeeded()); assertTrue(result.observed().isEmpty()); assertEquals(1, adapter.calls.size());
            assertEquals(PlatformModelDeploymentStatus.DRAFT, result.deployment().status());
        }
    }

    @Test void 第一请求后会话失效不执行下个样本且不能写成功() {
        var adapter = new Samples(); adapter.cancel = true;
        assertThrows(InvalidAuthenticatedSessionException.class,
                () -> service(adapter, List.of(price(ModelType.CHAT))).probe(command(), draft(ModelType.CHAT), connection(ModelType.CHAT)));
        assertEquals(1, adapter.calls.size()); assertNull(store.lastOutcome);
    }

    @Test void 同一目标存在歧义报价时启动即拒绝() {
        var adapter = new Samples(); var price = price(ModelType.CHAT);
        assertThrows(IllegalArgumentException.class, () -> service(adapter, List.of(price, price)));
    }

    private PlatformModelProbeProviderService service(Samples samples, List<PlatformCapabilityVerificationPrice> prices) {
        return new PlatformModelProbeProviderService(List.of(samples), List.of(samples), prices, store, CLOCK,
                identifiers::incrementAndGet, Duration.ofHours(24));
    }
    private ProbePlatformModelDeploymentCommand command() {
        return new ProbePlatformModelDeploymentCommand(new PlatformRequestContext(new SubjectId(101), CredentialType.PERSON, AccessChannel.CONSOLE,
                1, Set.of(PlatformPermissions.SESSION_ACCESS, PlatformPermissions.MODEL_PLATFORM_WRITE), "synthetic-capability"),
                "12", 0, Duration.ofSeconds(30), session::get);
    }
    private static PlatformModelDeployment draft(ModelType type) {
        var inputs = new PlatformModelDeploymentInputs("合成资料 v1", "合成驻留", type == ModelType.RERANK ? 3 : null,
                Set.of(201L), 100, "合成模型", PlatformModelCandidateClassification.CONFIRMED_BY_PROVIDER, 101, NOW.minusSeconds(30));
        return PlatformModelDeployment.createDraft(new PlatformModelDeploymentId(12), new PlatformProviderConnectionId(11), 2,
                "synthetic-model", "合成部署", type, Set.of(ModelCapability.valueOf(type.name())), 8192,
                type == ModelType.CHAT ? 128 : null, type == ModelType.EMBEDDING ? 2 : null, NOW.minusSeconds(30), inputs);
    }
    private static PlatformProviderConnection connection(ModelType type) {
        var connection = mock(PlatformProviderConnection.class);
        when(connection.id()).thenReturn(new PlatformProviderConnectionId(11)); when(connection.version()).thenReturn(2L);
        when(connection.providerKind()).thenReturn(com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderKind.OPENAI_COMPATIBLE);
        when(connection.preset()).thenReturn(new PlatformProviderPresetSelection("SILICONFLOW", "2026-10-21", type.name(), null));
        when(connection.endpoint()).thenReturn(URI.create("https://models.example.invalid/v1"));
        when(connection.credentialReference()).thenReturn(new CredentialReference("secret://platform-provider-connection/11"));
        return connection;
    }
    private static PlatformCapabilityVerificationPrice price(ModelType type) {
        return new PlatformCapabilityVerificationPrice(11, 2, "synthetic-model", type, "合成驻留", "a".repeat(64),
                NOW.minusSeconds(60), NOW.plusSeconds(600), "CNY", "PER_MILLION_TOKENS", BigDecimal.ONE, type == ModelType.CHAT ? BigDecimal.ONE : BigDecimal.ZERO, 1024, 1024);
    }

    private final class Samples implements PlatformCapabilitySampleExecutor, PlatformReranker {
        final List<PlatformCapabilitySampleRequest> calls = new ArrayList<>();
        int rerankCalls; boolean missingUsage; boolean badText; boolean cancel;
        public ProviderKind providerKind() { return ProviderKind.OPENAI_COMPATIBLE; }
        public PlatformCapabilitySampleResult execute(PlatformCapabilitySampleRequest request) { return sample(request); }
        public PlatformCapabilitySampleResult rerank(PlatformCapabilitySampleRequest request) { rerankCalls++; return sample(request); }
        private PlatformCapabilitySampleResult sample(PlatformCapabilitySampleRequest request) {
            assertTrue(store.reservations.get() > 0, "第一请求前必须已完整预留");
            int index = calls.size(); calls.add(request);
            if (cancel) session.set(false);
            String text = request.type().name().equals("CHAT") ? (badText ? "错误正文" : List.of("OK", "SAFE", "UNKNOWN").get(index)) : null;
            var vectors = request.type().name().equals("EMBEDDING") ? List.of(new PlatformCapabilitySampleResult.Embedding(0, List.of(1.0, 2.0))) : List.<PlatformCapabilitySampleResult.Embedding>of();
            var ranks = List.<PlatformCapabilitySampleResult.Ranked>of();
            if (request.type().name().equals("RERANK")) {
                int first = index == 0 ? 0 : 1;
                ranks = List.of(new PlatformCapabilitySampleResult.Ranked(first, .9), new PlatformCapabilitySampleResult.Ranked(first == 0 ? 1 : 0, .5),
                        new PlatformCapabilitySampleResult.Ranked(2, .1));
            }
            return new PlatformCapabilitySampleResult(request.binding(), request.type(), text, vectors, ranks,
                    missingUsage ? null : 10L, request.type().name().equals("CHAT") ? 1L : 0L, false);
        }
    }
    private static final class RecordingStore implements PlatformModelDeploymentVerificationStore {
        final AtomicInteger reservations = new AtomicInteger(); String lastOutcome; List<String> lastPassed;
        public void reserve(PlatformCapabilityVerificationReservation r, BooleanSupplier active) {
            assertTrue(active.getAsBoolean()); assertEquals(3, r.expectedSampleDigests().size());
            assertTrue(r.reservedFee().compareTo(new BigDecimal("0.03")) <= 0); reservations.incrementAndGet();
        }
        public void commit(PlatformCapabilityVerificationReservation r, PlatformModelDeploymentVerificationResult v,
                String outcome, int count, List<String> passed, BooleanSupplier active) {
            if (!active.getAsBoolean()) throw new InvalidAuthenticatedSessionException();
            lastOutcome = outcome; lastPassed = passed;
        }
    }
}
