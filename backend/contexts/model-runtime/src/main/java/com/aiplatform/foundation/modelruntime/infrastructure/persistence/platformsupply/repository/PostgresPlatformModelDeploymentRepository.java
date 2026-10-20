package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentIdempotencyConflictException;
import com.aiplatform.foundation.modelruntime.application.deployment.exception.ModelDeploymentNameConflictException;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelCandidateConfirmationStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerificationId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeploymentStatus;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.deployment.PlatformModelDeploymentInputs;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCreationOutcome;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentPage;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformModelDeploymentCounts;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformModelDeploymentRow;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentMapper;
import com.aiplatform.foundation.security.identity.TenantActivityFacts;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformModelDeploymentEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformModelDeploymentRequestEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformModelDeploymentRequestMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 同一短事务冻结来源、活动租户、未知分类确认、完整声明与幂等定位，不产生生效授权。 */
public final class PostgresPlatformModelDeploymentRepository implements PlatformModelDeploymentRepository {
    private final PlatformModelDeploymentMapper mapper;
    private final PlatformModelDeploymentRequestMapper requests;
    private final TransactionTemplate transactions;
    private final TransactionTemplate reads;
    private final PlatformProviderModelDiscoveryLookup discoveries;
    private final PlatformProviderModelCandidateConfirmationStore confirmations;
    private final java.util.function.Supplier<TenantActivityFacts> tenants;
    private final Clock clock;

    public PostgresPlatformModelDeploymentRepository(PlatformModelDeploymentMapper mapper, PlatformModelDeploymentRequestMapper requests, PlatformTransactionManager manager,
            PlatformProviderModelDiscoveryLookup discoveries, PlatformProviderModelCandidateConfirmationStore confirmations,
            java.util.function.Supplier<TenantActivityFacts> tenants, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.requests = Objects.requireNonNull(requests);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        this.reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.discoveries = Objects.requireNonNull(discoveries);
        this.confirmations = Objects.requireNonNull(confirmations);
        this.tenants = Objects.requireNonNull(tenants);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<java.time.Instant> lastVerifiedAt(PlatformModelDeploymentId id) { return Optional.ofNullable(mapper.lastVerifiedAt(id.value())); }

    @Override
    public PlatformModelDeploymentCounts counts() { return mapper.counts(); }

    @Override
    public PlatformModelDeploymentCreationOutcome create(PlatformModelDeploymentCreation creation) {
        Objects.requireNonNull(creation);
        return Objects.requireNonNull(transactions.execute(status -> createInTransaction(creation)));
    }

    @Override
    public Optional<PlatformModelDeployment> findById(PlatformModelDeploymentId id) {
        return Optional.ofNullable(mapper.selectById(Objects.requireNonNull(id).value())).map(PostgresPlatformModelDeploymentRepository::toDomain);
    }

    @Override
    public PlatformModelDeploymentPage findPage(long number, long size) {
        if (number < 1 || size < 1 || size > 100) throw new IllegalArgumentException("分页参数不正确");
        return Objects.requireNonNull(reads.execute(status -> {
            var page = mapper.selectPage(new Page<PlatformModelDeploymentRow>(number, size));
            return new PlatformModelDeploymentPage(page.getRecords().stream().map(PostgresPlatformModelDeploymentRepository::toDomain).toList(), page.getTotal());
        }));
    }

    @Override
    public boolean update(PlatformModelDeployment deployment, long expectedVersion) {
        if (deployment.version() != expectedVersion + 1) throw new IllegalArgumentException("平台模型部署新旧版本关系不正确");
        return mapper.update(null, Wrappers.<PlatformModelDeploymentEntity>lambdaUpdate()
                .eq(PlatformModelDeploymentEntity::getId, deployment.id().value()).eq(PlatformModelDeploymentEntity::getScope, "PLATFORM")
                .eq(PlatformModelDeploymentEntity::getVersion, expectedVersion).set(PlatformModelDeploymentEntity::getStatus, deployment.status().name())
                .set(PlatformModelDeploymentEntity::getLastVerificationId, deployment.lastVerificationId() == null ? null : deployment.lastVerificationId().value())
                .set(PlatformModelDeploymentEntity::getVerificationExpiresAt, deployment.verificationExpiresAt())
                .set(PlatformModelDeploymentEntity::getVersion, deployment.version())) == 1;
    }

    private PlatformModelDeploymentCreationOutcome createInTransaction(PlatformModelDeploymentCreation creation) {
        mapper.lockCreation();
        var key = sha256(creation.idempotencyKey());
        var existing = requests.selectById(key);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(creation.requestFingerprint())) throw new ModelDeploymentIdempotencyConflictException();
            return new PlatformModelDeploymentCreationOutcome(findById(new PlatformModelDeploymentId(existing.getPlatformModelDeploymentId())).orElseThrow(), true);
        }
        var d = creation.deployment();
        var input = Objects.requireNonNull(d.inputs(), "新草稿必须声明完整输入");
        var now = clock.instant();
        if (mapper.lockSource(d.providerConnectionId().value(), d.providerConnectionRevision(), now) == null)
            throw new com.aiplatform.foundation.shared.kernel.error.ApplicationException(
                    com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode.DEPLOYMENT_DISCOVERY_UNAVAILABLE);
        var current = discoveries.findCurrentSnapshot(d.providerConnectionId(), d.providerConnectionRevision(), d.modelCode(), d.modelType(), now)
                .orElseThrow(() -> new com.aiplatform.foundation.shared.kernel.error.ApplicationException(
                    com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode.DEPLOYMENT_DISCOVERY_UNAVAILABLE));
        if (current.discoveryId() != input.discoveryId() || !Objects.equals(current.candidate().displayName(), input.candidateDisplayName()))
            throw new com.aiplatform.foundation.shared.kernel.error.ApplicationException(
                    com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode.DEPLOYMENT_DISCOVERY_UNAVAILABLE);
        // 读取进程不装配组织写用例；只有创建边界才要求真实拥有者，缺失必须在任何写入前拒绝。
        Objects.requireNonNull(tenants.get(), "活动租户事实不可用").requireActive(input.plannedTenantIds());
        var candidate = current.candidate();
        if (candidate.classification() == PlatformModelCandidateClassification.REQUIRES_ADMIN_CONFIRMATION) {
            candidate = confirmations.confirmCurrentCandidate(d.providerConnectionId(), d.providerConnectionRevision(), d.modelCode(),
                    d.modelType(), input.declaredBySubjectId(), input.declaredAt(), now);
        }
        if (candidate.modelType() != d.modelType() || !candidate.capabilities().equals(d.capabilities())
                || candidate.classification() != input.classification()) throw new com.aiplatform.foundation.shared.kernel.error.ApplicationException(
                    com.aiplatform.foundation.modelruntime.domain.common.error.ModelRuntimeErrorCode.DEPLOYMENT_DISCOVERY_UNAVAILABLE);
        try {
            mapper.insertDeployment(d, d.capabilities().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                    input.plannedTenantIds().stream().sorted().map(Object::toString).collect(Collectors.joining(",")));
            var request = new PlatformModelDeploymentRequestEntity();
            request.setIdempotencyKeyHash(key); request.setRequestFingerprint(creation.requestFingerprint());
            request.setPlatformModelDeploymentId(d.id().value()); requests.insert(request);
        } catch (DuplicateKeyException failure) { throw new ModelDeploymentNameConflictException(); }
        return new PlatformModelDeploymentCreationOutcome(d, false);
    }

    private static PlatformModelDeployment toDomain(PlatformModelDeploymentRow row) {
        var inputs = row.parameterSource() == null ? null : new PlatformModelDeploymentInputs(row.parameterSource(), row.residency(),
                row.rerankCandidateLimit(), Arrays.stream(row.plannedTenantIds().split(",")).map(Long::valueOf).collect(Collectors.toUnmodifiableSet()),
                row.discoveryId(), row.candidateDisplayName(), PlatformModelCandidateClassification.valueOf(row.classification()), row.subjectId(), row.declaredAt());
        return PlatformModelDeployment.restore(new PlatformModelDeploymentId(row.id()), new PlatformProviderConnectionId(row.connectionId()),
                row.revision(), row.modelCode(), row.displayName(), ModelType.valueOf(row.modelType()),
                Arrays.stream(row.capabilities().split(",")).map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet()),
                row.contextWindow(), row.outputLimit(), row.embeddingDimension(), row.verificationId() == null ? null : new PlatformModelCapabilityVerificationId(row.verificationId()),
                row.expiresAt(), PlatformModelDeploymentStatus.valueOf(row.status()), row.version(), row.createdAt(), inputs);
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("当前 JDK 不支持 SHA-256", failure); }
    }
}
