package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelCandidateConfirmationStore;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformDiscoveredModelCandidate;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery.PlatformModelCandidateClassification;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL 在同一短事务内提交连接验证修订、平台模型发现根与候选。 */
public final class PostgresPlatformProviderModelDiscoveryStore
        implements PlatformProviderModelDiscoveryStore, PlatformProviderModelDiscoveryLookup,
        PlatformProviderModelCandidateConfirmationStore {

    private final com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper candidateMapper;
    private final TransactionTemplate reads;
    private final TransactionTemplate transactions;
    private final PlatformProviderConnectionRepository connections;

    public PostgresPlatformProviderModelDiscoveryStore(
            PlatformTransactionManager transactionManager,
            PlatformProviderConnectionRepository connections,
            com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper candidateMapper) {
        this.candidateMapper = Objects.requireNonNull(candidateMapper, "候选分页映射器不能为空");
        this.reads = new TransactionTemplate(transactionManager);
        reads.setReadOnly(true);
        reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
        this.connections = Objects.requireNonNull(connections, "平台连接仓储不能为空");
    }

    @Override
    public void record(PlatformProviderConnection connection, long expectedVersion,
            PlatformProviderModelDiscovery discovery) {
        Objects.requireNonNull(connection, "平台连接不能为空");
        Objects.requireNonNull(discovery, "平台模型发现事实不能为空");
        if (expectedVersion < 0 || connection.version() != expectedVersion + 1
                || !connection.id().equals(discovery.connectionId())
                || connection.version() != discovery.connectionRevision()
                || !Objects.equals(connection.lastProbeSucceeded(), discovery.succeeded())
                || !Objects.equals(connection.lastProbeCode(), discovery.resultCode())
                || !Objects.equals(connection.lastProbedAt(), discovery.discoveredAt())) {
            throw new IllegalArgumentException("平台模型发现必须绑定本次连接验证的新修订");
        }
        transactions.executeWithoutResult(status -> {
            // 先通过 CAS 占用当前修订，停用或轮换后的迟到响应不能留下发现事实。
            if (!connections.update(connection, expectedVersion)) {
                throw new ProviderConnectionConcurrentModificationException();
            }
            recordInTransaction(discovery);
        });
    }

    @Override
    public PageResult<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeployableModel> findDeployableModels(PageQuery page, Instant now) {
        return Objects.requireNonNull(reads.execute(status -> {
            var result = candidateMapper.selectDeployablePage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<
                    com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformDeployableModelRow>(page.pageNumber(), page.pageSize()), now);
            return PageResult.of(result.getRecords().stream().map(row -> new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformDeployableModel(
                    row.connectionId(), row.connectionRevision(), row.connectionDisplayName(), row.residency(), row.declaredCapability(), row.modelCode(),
                    row.displayName(), row.modelType() == null ? null : ModelType.valueOf(row.modelType()),
                    Arrays.stream(row.capabilities().split(",")).filter(v -> !v.isEmpty()).map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet()),
                    row.classification())).toList(), page, result.getTotal());
        }));
    }

    @Override
    public Optional<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformModelCandidateSnapshot> findCurrentSnapshot(
            PlatformProviderConnectionId connectionId, long revision, String code, ModelType type, Instant now) {
        return Optional.ofNullable(candidateMapper.selectDraftCandidate(connectionId.value(), revision, code, type.name(), now))
                .map(row -> new com.aiplatform.foundation.modelruntime.application.platformsupply.dto.deployment.PlatformModelCandidateSnapshot(row.discoveryId(), candidate(row)));
    }

    @Override
    public Optional<PlatformDiscoveredModelCandidate> findCurrentCandidate(
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            String modelCode,
            ModelType modelType,
            Instant now) {
        connectionId = Objects.requireNonNull(connectionId, "平台供应商连接标识不能为空");
        modelCode = Objects.requireNonNull(modelCode, "模型代码不能为空");
        modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        now = Objects.requireNonNull(now, "当前时间不能为空");
        if (connectionRevision < 0) throw new IllegalArgumentException("平台供应商连接修订不能为负数");
        return Optional.ofNullable(candidateMapper.selectCurrentCandidate(
                connectionId.value(), connectionRevision, modelCode, modelType.name(), now))
                .map(PostgresPlatformProviderModelDiscoveryStore::candidate);
    }

    @Override
    public PageResult<PlatformDiscoveredModelCandidate> findCurrentCandidates(
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            PageQuery page,
            Instant now, String q) {
        Objects.requireNonNull(connectionId, "平台供应商连接标识不能为空");
        Objects.requireNonNull(page, "分页查询不能为空");
        Objects.requireNonNull(now, "当前时间不能为空");
        if (connectionRevision < 0) throw new IllegalArgumentException("平台供应商连接修订不能为负数");
        var pattern = "%" + Objects.requireNonNull(q, "搜索词不能为空")
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        // 同一只读快照生成数据与总数；不能让重发现或确认夹在两次查询之间。
        return Objects.requireNonNull(reads.execute(status -> {
            var pagination = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<
                    com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderModelCandidateRow>(page.pageNumber(), page.pageSize());
            pagination.setCountId("countCandidates");
            var result = candidateMapper.selectCandidatePage(pagination, connectionId.value(), connectionRevision, now, pattern);
            var items = result.getRecords().stream()
                    .map(PostgresPlatformProviderModelDiscoveryStore::candidate).toList();
            return PageResult.of(items, page, result.getTotal());
        }), "候选只读查询没有返回结果");
    }

    @Override
    public PlatformDiscoveredModelCandidate confirmCurrentCandidate(
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            String modelCode,
            ModelType modelType,
            long confirmedBySubjectId,
            Instant confirmedAt,
            Instant now) {
        connectionId = Objects.requireNonNull(connectionId, "平台供应商连接标识不能为空");
        modelCode = Objects.requireNonNull(modelCode, "模型代码不能为空");
        modelType = Objects.requireNonNull(modelType, "模型类型不能为空");
        confirmedAt = Objects.requireNonNull(confirmedAt, "确认时间不能为空");
        now = Objects.requireNonNull(now, "当前时间不能为空");
        if (connectionRevision < 0 || confirmedBySubjectId <= 0) {
            throw new IllegalArgumentException("平台连接修订或确认主体不正确");
        }
        var capability = ModelCapability.valueOf(modelType.name());
        var id = connectionId;
        var code = modelCode;
        var type = modelType;
        var confirmationTime = confirmedAt;
        var currentTime = now;
        // 分类事实与名称回读在同一事务；过期或修订变更不能留下一个无法回读的成功响应。
        return Objects.requireNonNull(transactions.execute(status -> {
            if (candidateMapper.confirmCandidate(id.value(), connectionRevision, code, type.name(),
                    capability.name(), confirmedBySubjectId, confirmationTime, currentTime) != 1) {
                throw new IllegalArgumentException("当前模型候选不存在、已过期或已经确认");
            }
            return findCurrentCandidate(id, connectionRevision, code, type, currentTime)
                    .orElseThrow(() -> new IllegalArgumentException("当前模型候选不存在、已过期或已经确认"));
        }), "候选确认没有返回结果");
    }

    private static PlatformDiscoveredModelCandidate candidate(
            com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderModelCandidateRow row) {
        return new PlatformDiscoveredModelCandidate(row.modelCode(),
                row.modelType() == null ? null : ModelType.valueOf(row.modelType()),
                Arrays.stream(row.capabilities().split(",", -1)).filter(value -> !value.isEmpty())
                        .map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet()),
                PlatformModelCandidateClassification.valueOf(row.classification()), row.displayName());
    }

    private void recordInTransaction(PlatformProviderModelDiscovery discovery) {
        candidateMapper.insertDiscovery(discovery.id().value(), discovery.connectionId().value(),
                discovery.connectionRevision(), discovery.succeeded(), discovery.resultCode(),
                discovery.elapsed().toMillis(), discovery.discoveredAt(), discovery.expiresAt());
        for (var model : discovery.models()) {
            candidateMapper.insertCandidate(discovery.id().value(), model.modelCode(),
                    model.modelType() == null ? null : model.modelType().name(),
                    model.capabilities().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                    model.classification().name(), model.displayName());
        }
    }
}
