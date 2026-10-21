package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformCapabilityVerificationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentMapper;
import com.aiplatform.foundation.security.identity.PlatformModelWriteFacts;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 预留和最终提交各用短事务；网络期间不持有连接、部署或权限锁。 */
public final class PostgresPlatformModelDeploymentVerificationStore implements PlatformModelDeploymentVerificationStore {
    private final PlatformCapabilityVerificationMapper mapper;
    private final PlatformModelDeploymentMapper deployments;
    private final PlatformModelDeploymentRepository repository;
    private final PlatformProviderModelDiscoveryLookup discoveries;
    private final Supplier<PlatformModelWriteFacts> actors;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public PostgresPlatformModelDeploymentVerificationStore(PlatformCapabilityVerificationMapper mapper,
            PlatformModelDeploymentMapper deployments, PlatformModelDeploymentRepository repository,
            PlatformProviderModelDiscoveryLookup discoveries, Supplier<PlatformModelWriteFacts> actors,
            PlatformTransactionManager manager, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper); this.deployments = Objects.requireNonNull(deployments);
        this.repository = Objects.requireNonNull(repository); this.discoveries = Objects.requireNonNull(discoveries);
        this.actors = Objects.requireNonNull(actors); this.clock = Objects.requireNonNull(clock);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        this.transactions.setTimeout(3);
    }

    @Override
    public void reserve(PlatformCapabilityVerificationReservation reservation, BooleanSupplier sessionActive) {
        Objects.requireNonNull(reservation);
        requireManifest(reservation);
        transactions.executeWithoutResult(status -> {
            requireCurrent(reservation, sessionActive);
            requireLive(reservation, sessionActive, true);
            mapper.expire(reservation.deployment().id().value(), clock.instant());
            if (mapper.reserve(reservation, String.join(",", reservation.expectedSampleDigests())) != 1)
                throw new IllegalStateException("整轮费用预留未保存");
            requireLive(reservation, sessionActive, true);
        });
    }

    @Override
    public void commit(PlatformCapabilityVerificationReservation reservation,
            PlatformModelDeploymentVerificationResult result, String outcome, int attemptedRequests,
            List<String> passedSampleDigests, BooleanSupplier sessionActive) {
        Objects.requireNonNull(reservation); Objects.requireNonNull(result);
        var passed = List.copyOf(passedSampleDigests);
        requireReceipt(reservation, result, outcome, attemptedRequests, passed);
        transactions.executeWithoutResult(status -> {
            requireCurrent(reservation, sessionActive);
            if (!reservation.planFingerprint().equals(mapper.lockReservation(reservation)))
                throw new IllegalStateException("验证预留已完成、退休或绑定不符");
            requireLive(reservation, sessionActive, result.verification().succeeded());
            var verification = result.verification();
            if (mapper.insertVerification(verification, capabilities(verification.declaredCapabilities()),
                    capabilities(verification.observedCapabilities()), verification.elapsed().toMillis()) != 1)
                throw new IllegalStateException("验证事实未保存");
            if (verification.succeeded() && mapper.updateVerified(result.revisedDeployment(), reservation.deployment().version()) != 1)
                throw new IllegalStateException("平台模型部署修订已变化");
            if (mapper.finish(reservation, outcome, attemptedRequests, String.join(",", passed), clock.instant()) != 1)
                throw new IllegalStateException("验证预留终态未保存");
            // 独立审计最后写，任何失败均回滚验证、CAS 和终态；外呼前的保守预留继续保留。
            if (mapper.audit(reservation, verification.id().value(), verification.succeeded(), clock.instant()) != 1)
                throw new IllegalStateException("平台模型验证审计未保存");
            requireLive(reservation, sessionActive, verification.succeeded());
        });
    }

    private void requireCurrent(PlatformCapabilityVerificationReservation reservation, BooleanSupplier sessionActive) {
        requireLive(reservation, sessionActive, false);
        var facts = actors.get();
        if (facts == null) throw new IllegalStateException("当前平台权限事实未装配");
        facts.requireCurrent(reservation.actor());
        var target = reservation.deployment();
        var now = clock.instant();
        // 与创建保持来源先于部署的锁顺序，轮换、停用和新发现均不能穿越提交。
        if (deployments.lockSource(target.providerConnectionId().value(), target.providerConnectionRevision(), now) == null
                || mapper.lockDeployment(target.id().value()) == null) throw new IllegalStateException("冻结来源或部署不可用");
        var current = repository.findById(target.id()).orElseThrow(() -> new IllegalStateException("部署不存在"));
        if (current.status() != PlatformModelDeploymentStatus.DRAFT || current.version() != target.version()
                || !current.providerConnectionId().equals(target.providerConnectionId())
                || current.providerConnectionRevision() != target.providerConnectionRevision()
                || !current.modelCode().equals(target.modelCode()) || current.modelType() != target.modelType()
                || !current.capabilities().equals(target.capabilities()) || current.contextWindow() != target.contextWindow()
                || !Objects.equals(current.outputLimit(), target.outputLimit())
                || !Objects.equals(current.embeddingDimension(), target.embeddingDimension())
                || !Objects.equals(current.inputs(), target.inputs())) throw new IllegalStateException("部署声明已变化");
        var snapshot = discoveries.findCurrentSnapshot(target.providerConnectionId(), target.providerConnectionRevision(),
                target.modelCode(), target.modelType(), now).orElseThrow(() -> new IllegalStateException("冻结候选已失效"));
        if (snapshot.discoveryId() != target.inputs().discoveryId()) throw new IllegalStateException("发现根已变化");
    }

    private void requireLive(PlatformCapabilityVerificationReservation reservation, BooleanSupplier sessionActive, boolean success) {
        if (Thread.currentThread().isInterrupted() || !Objects.requireNonNull(sessionActive).getAsBoolean())
            throw new InvalidAuthenticatedSessionException();
        var now = clock.instant();
        if (success && (!reservation.deadline().isAfter(now) || !reservation.quoteExpiresAt().isAfter(now)))
            throw new IllegalStateException("验证或报价已过期");
    }

    private static void requireReceipt(PlatformCapabilityVerificationReservation r,
            PlatformModelDeploymentVerificationResult result, String outcome, int count, List<String> passed) {
        requireManifest(r);
        var v = result.verification(); var d = r.deployment();
        var revised = result.revisedDeployment();
        var allowed = Set.of("PASSED", "PRICING_UNAVAILABLE", "BUDGET_EXCEEDED", "INVALID_RESPONSE", "USAGE_EXCEEDED",
                "DEADLINE_EXCEEDED", "CANCELLED", "CALL_FAILED", "ALREADY_RUN");
        if (!allowed.contains(outcome) || count < 0 || count > 3 || passed.size() > count
                || !r.expectedSampleDigests().subList(0, passed.size()).equals(passed)
                || !v.deploymentId().equals(d.id()) || v.deploymentRevision() != d.version()
                || !v.providerConnectionId().equals(d.providerConnectionId())
                || v.providerConnectionRevision() != d.providerConnectionRevision() || !v.modelCode().equals(d.modelCode())
                || !v.declaredCapabilities().equals(d.capabilities()) || v.probedAt().isBefore(r.startedAt())
                || !v.resultCode().equals("S2_" + outcome) || v.succeeded() != "PASSED".equals(outcome)
                || !revised.id().equals(d.id()) || !Objects.equals(revised.inputs(), d.inputs())
                || !revised.providerConnectionId().equals(d.providerConnectionId()) || revised.providerConnectionRevision() != d.providerConnectionRevision()
                || !revised.modelCode().equals(d.modelCode()) || revised.modelType() != d.modelType()
                || !revised.capabilities().equals(d.capabilities()) || revised.contextWindow() != d.contextWindow()
                || !Objects.equals(revised.outputLimit(), d.outputLimit()) || !Objects.equals(revised.embeddingDimension(), d.embeddingDimension())
                || (v.succeeded() ? count != 3 || !passed.equals(r.expectedSampleDigests()) || !v.observedCapabilities().equals(d.capabilities())
                    || revised.status() != PlatformModelDeploymentStatus.VERIFIED || revised.version() != d.version() + 1
                    || !v.id().equals(revised.lastVerificationId()) || !Objects.equals(revised.verificationExpiresAt(), v.expiresAt())
                    || v.expiresAt() == null || !v.expiresAt().isAfter(v.probedAt())
                    || v.expiresAt().isAfter(v.probedAt().plus(Duration.ofHours(24)))
                    : !v.observedCapabilities().isEmpty() || revised.status() != PlatformModelDeploymentStatus.DRAFT
                        || revised.version() != d.version() || revised.lastVerificationId() != null))
            throw new IllegalArgumentException("验证回执不符合冻结三样本与精确目标");
    }

    private static void requireManifest(PlatformCapabilityVerificationReservation reservation) {
        if (!com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformCapabilitySafetySuite.VERSION.equals(reservation.sampleVersion())
                || !com.aiplatform.foundation.modelruntime.application.platformsupply.service.PlatformCapabilitySafetySuite.sampleDigests(
                        reservation.deployment().modelType()).equals(reservation.expectedSampleDigests()))
            throw new IllegalArgumentException("只接受当前固定版本的完整有序安全样本清单");
    }

    private static String capabilities(Set<com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability> values) {
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
