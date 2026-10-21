package com.aiplatform.foundation.modelruntime.application.platformsupply.service;

import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformCapabilitySampleRequest;
import com.aiplatform.foundation.modelruntime.application.platformsupply.command.ProbePlatformModelDeploymentCommand;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationPrice;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformCapabilitySampleExecutor;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification.PlatformReranker;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapabilityObservation;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerification;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 以精确目标、完整预留及同一三样本内核编排真实协议，最终验证仍经短事务独立复核。 */
public final class PlatformModelProbeProviderService {
    private final Map<String, PlatformCapabilitySampleExecutor> executors;
    private final Map<String, PlatformReranker> rerankers;
    private final List<PlatformCapabilityVerificationPrice> prices;
    private final PlatformModelDeploymentVerificationStore store;
    private final Clock clock;
    private final EntityIdGenerator identifiers;
    private final Duration validity;

    public PlatformModelProbeProviderService(List<PlatformCapabilitySampleExecutor> executors,
            List<PlatformReranker> rerankers, List<PlatformCapabilityVerificationPrice> prices,
            PlatformModelDeploymentVerificationStore store, Clock clock, EntityIdGenerator identifiers, Duration validity) {
        this.executors = index(executors, executor -> executor.providerKind().name());
        this.rerankers = index(rerankers, reranker -> reranker.providerKind().name());
        this.prices = List.copyOf(prices); this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock); this.identifiers = Objects.requireNonNull(identifiers);
        if (validity == null || validity.isNegative() || validity.isZero()) throw new IllegalArgumentException("验证有效期不正确");
        this.validity = validity;
        var keys = prices.stream().map(p -> List.of(p.connectionId(), p.connectionRevision(), p.modelCode(), p.modelType(), p.residency())).toList();
        if (keys.stream().distinct().count() != keys.size()) throw new IllegalArgumentException("同一精确目标不得配置歧义报价");
    }

    /** 只验证完整草稿；报价或对应协议缺失时零外呼、零预留且不提升状态。 */
    public Result probe(ProbePlatformModelDeploymentCommand command, PlatformModelDeployment deployment,
            PlatformProviderConnection connection) {
        if (deployment.inputs() == null || deployment.status() != PlatformModelDeploymentStatus.DRAFT || connection.preset() == null)
            throw new IllegalStateException("只有完整声明草稿可执行三样本验证");
        var startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        var target = target(deployment);
        var price = prices.stream().filter(p -> p.connectionId() == connection.id().value()
                && p.connectionRevision() == connection.version() && p.modelCode().equals(deployment.modelCode())
                && p.modelType() == deployment.modelType() && p.residency().equals(deployment.inputs().residency())).findFirst().orElse(null);
        var tariff = price == null ? null : new PlatformCapabilityVerificationSession.Tariff(target.fingerprint(),
                price.evidenceDigest(), price.observedAt(), price.expiresAt(), price.currency(),
                PlatformCapabilityVerificationSession.BillingUnit.valueOf(price.billingUnit()), price.inputPrice(),
                price.outputPrice(), price.inputUnitsUpperBound(), price.inputTokensUpperBound());
        String kind = connection.providerKind().name();
        if (deployment.modelType() == ModelType.RERANK ? !rerankers.containsKey(kind) : !executors.containsKey(kind))
            return new Result(deployment, false, Set.of(), Duration.ZERO, "S2_PROTOCOL_UNAVAILABLE", startedAt);
        var reserved = new AtomicReference<PlatformCapabilityVerificationReservation>();
        var session = new PlatformCapabilityVerificationSession(clock, System::nanoTime, command.timeout());
        var summary = session.run(target, tariff, PlatformCapabilityVerificationSession.FEE_LIMIT, () -> !active(command), request -> {
            long callbackStarted = System.nanoTime();
            if (reserved.get() == null) {
                var reservation = new PlatformCapabilityVerificationReservation(identifiers.nextId(), command.context(), deployment,
                        target.fingerprint(), target.inputsDigest(), request.planFingerprint(), price.evidenceDigest(), price.expiresAt(),
                        PlatformCapabilitySafetySuite.VERSION,
                        PlatformCapabilitySafetySuite.samples(deployment.modelType()).stream().map(PlatformCapabilitySafetySuite.Sample::fingerprint).toList(),
                        request.reservedFee(), startedAt, startedAt.plus(command.timeout()));
                store.reserve(reservation, command.sessionActive());
                reserved.set(reservation);
            }
            // 调度和预留事务都计入整轮期限，网络不能沿用预留之前的剩余时长。
            long remaining = request.timeout().toNanos() - (System.nanoTime() - callbackStarted);
            if (remaining <= 0 || !active(command) || Thread.currentThread().isInterrupted()) return null;
            var sample = new PlatformCapabilitySampleRequest(request.binding(),
                    com.aiplatform.foundation.modelruntime.application.extension.facade.ModelType.valueOf(deployment.modelType().name()),
                    connection.preset().providerId(), connection.endpoint(), connection.credentialReference().value(), connection.version(), deployment.modelCode(),
                    request.sample().input(), request.sample().documents(), request.maxOutputTokens(),
                    target.embeddingDimension(), target.rerankCandidateLimit(), Duration.ofNanos(remaining));
            var observation = deployment.modelType() == ModelType.RERANK
                    ? rerankers.get(kind).rerank(sample) : executors.get(kind).execute(sample);
            return reply(observation, tariff);
        });
        var completedAt = clock.instant();
        var elapsed = Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos));
        boolean succeeded = summary.outcome() == PlatformCapabilityVerificationSession.Outcome.PASSED;
        var observed = succeeded ? deployment.capabilities() : Set.<ModelCapability>of();
        String code = "S2_" + summary.outcome().name();
        var revised = deployment;
        if (reserved.get() != null) {
            var result = PlatformModelDeploymentVerification.verify(deployment,
                    new PlatformModelCapabilityVerificationId(identifiers.nextId()),
                    new ModelCapabilityObservation(succeeded, observed, code, elapsed, completedAt), completedAt.plus(validity));
            store.commit(reserved.get(), result, summary.outcome().name(), summary.attemptedRequests(),
                    summary.passedSampleDigests(), command.sessionActive());
            revised = result.revisedDeployment();
        }
        return new Result(revised, succeeded, observed, elapsed, code, completedAt);
    }

    private static boolean active(ProbePlatformModelDeploymentCommand command) {
        try { return command.sessionActive().getAsBoolean(); } catch (RuntimeException exception) { return false; }
    }

    private static PlatformCapabilitySafetySuite.Reply reply(PlatformCapabilitySampleResult result, PlatformCapabilityVerificationSession.Tariff tariff) {
        if (result == null) return null;
        var usage = result.inputTokens() == null || result.outputTokens() == null ? null
                : new PlatformCapabilitySafetySuite.Usage(tariff.unit() == PlatformCapabilityVerificationSession.BillingUnit.PER_REQUEST
                        ? 1 : result.inputTokens(), result.inputTokens(), result.outputTokens());
        return new PlatformCapabilitySafetySuite.Reply(result.binding(), ModelType.valueOf(result.type().name()), result.text(),
                result.embeddings().stream().map(v -> new PlatformCapabilitySafetySuite.Embedding(v.index(), v.values())).toList(),
                result.ranks().stream().map(r -> new PlatformCapabilitySafetySuite.Ranked(r.index(), r.score())).toList(), usage, result.unexpectedContent());
    }

    private static PlatformCapabilityVerificationSession.Target target(PlatformModelDeployment deployment) {
        var input = deployment.inputs();
        var inputsDigest = PlatformCapabilitySafetySuite.digest(input.parameterSource(), input.residency(),
                Objects.toString(input.rerankCandidateLimit(), ""), input.plannedTenantIds().stream().sorted().map(Object::toString).collect(Collectors.joining(",")),
                Long.toString(input.discoveryId()), Objects.toString(input.candidateDisplayName(), ""), input.classification().name(),
                Long.toString(input.declaredBySubjectId()), input.declaredAt().toString());
        return new PlatformCapabilityVerificationSession.Target(deployment.id().value(), deployment.version(),
                deployment.providerConnectionId().value(), deployment.providerConnectionRevision(), deployment.modelCode(), deployment.modelType(),
                input.residency(), inputsDigest, deployment.contextWindow(), deployment.outputLimit() == null ? 0 : deployment.outputLimit(),
                deployment.embeddingDimension() == null ? 0 : deployment.embeddingDimension(), input.rerankCandidateLimit() == null ? 0 : input.rerankCandidateLimit());
    }

    private static <T> Map<String, T> index(List<T> adapters, Function<T, String> kind) {
        return adapters.stream().collect(Collectors.toUnmodifiableMap(kind, Function.identity()));
    }

    /** 只传出稳定分类及部署事实，不传出样本正文、向量或厂商原始异常。 */
    public record Result(PlatformModelDeployment deployment, boolean succeeded, Set<ModelCapability> observed,
            Duration elapsed, String code, Instant at) {}
}
