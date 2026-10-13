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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL 在同一短事务内提交连接验证修订、平台模型发现根与候选。 */
public final class PostgresPlatformProviderModelDiscoveryStore
        implements PlatformProviderModelDiscoveryStore, PlatformProviderModelDiscoveryLookup,
        PlatformProviderModelCandidateConfirmationStore {

    private final com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper candidateMapper;
    private final TransactionTemplate reads;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PlatformProviderConnectionRepository connections;

    public PostgresPlatformProviderModelDiscoveryStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PlatformProviderConnectionRepository connections,
            com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderModelCandidateMapper candidateMapper) {
        this.candidateMapper = Objects.requireNonNull(candidateMapper, "候选分页映射器不能为空");
        this.reads = new TransactionTemplate(transactionManager);
        reads.setReadOnly(true);
        reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 模板不能为空");
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
        return Optional.ofNullable(jdbc.query("""
                SELECT candidate.model_code,
                       COALESCE(confirmation.model_type, candidate.model_type) AS model_type,
                       CASE WHEN confirmation.discovery_id IS NOT NULL
                           THEN 'CONFIRMED_BY_ADMIN' ELSE candidate.classification END AS classification,
                       COALESCE(array_to_string(confirmation.capabilities, ','),
                                array_to_string(candidate.capabilities, ',')) AS capabilities
                FROM model_runtime.platform_provider_model_discovery discovery
                JOIN model_runtime.platform_discovered_model candidate ON candidate.discovery_id = discovery.id
                LEFT JOIN model_runtime.platform_model_candidate_confirmation confirmation
                  ON confirmation.discovery_id = candidate.discovery_id
                 AND confirmation.model_code = candidate.model_code
                WHERE discovery.platform_provider_connection_id = ?
                  AND discovery.platform_provider_connection_revision = ?
                  AND discovery.succeeded = TRUE
                  AND discovery.expires_at > ?
                  AND candidate.model_code = ?
                  AND COALESCE(confirmation.model_type, candidate.model_type) = ?
                  AND (candidate.classification <> 'REQUIRES_ADMIN_CONFIRMATION'
                       OR confirmation.discovery_id IS NOT NULL)
                ORDER BY discovery.discovered_at DESC, discovery.id DESC
                LIMIT 1
                """, rs -> rs.next() ? new PlatformDiscoveredModelCandidate(
                        rs.getString("model_code"), ModelType.valueOf(rs.getString("model_type")),
                        Arrays.stream(rs.getString("capabilities").split(",", -1))
                                .filter(value -> !value.isEmpty())
                                .map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet()),
                        PlatformModelCandidateClassification.valueOf(rs.getString("classification"))) : null,
                connectionId.value(), connectionRevision, Timestamp.from(now), modelCode, modelType.name()));
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
            var items = result.getRecords().stream().map(row -> new PlatformDiscoveredModelCandidate(row.modelCode(),
                    row.modelType() == null ? null : ModelType.valueOf(row.modelType()),
                    Arrays.stream(row.capabilities().split(",", -1)).filter(value -> !value.isEmpty())
                            .map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet()),
                    PlatformModelCandidateClassification.valueOf(row.classification()))).toList();
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
        var capability = modelType == ModelType.CHAT ? ModelCapability.CHAT : ModelCapability.EMBEDDING;
        var updated = jdbc.update("""
                INSERT INTO model_runtime.platform_model_candidate_confirmation
                    (discovery_id, model_code, model_type, capabilities,
                     confirmed_by_subject_id, confirmed_at)
                SELECT candidate.discovery_id, candidate.model_code, ?, ARRAY[?]::VARCHAR[], ?, ?
                FROM model_runtime.platform_discovered_model candidate
                WHERE candidate.discovery_id = (
                    SELECT discovery.id
                    FROM model_runtime.platform_provider_model_discovery discovery
                    WHERE discovery.platform_provider_connection_id = ?
                      AND discovery.platform_provider_connection_revision = ?
                      AND discovery.succeeded = TRUE
                      AND discovery.expires_at > ?
                    ORDER BY discovery.discovered_at DESC, discovery.id DESC
                    LIMIT 1
                )
                  AND candidate.model_code = ?
                  AND candidate.classification = 'REQUIRES_ADMIN_CONFIRMATION'
                  AND NOT EXISTS (
                    SELECT 1 FROM model_runtime.platform_model_candidate_confirmation confirmation
                    WHERE confirmation.discovery_id = candidate.discovery_id
                      AND confirmation.model_code = candidate.model_code
                  )
                """, modelType.name(), capability.name(), confirmedBySubjectId,
                Timestamp.from(confirmedAt), connectionId.value(), connectionRevision,
                Timestamp.from(now), modelCode);
        if (updated != 1) {
            throw new IllegalArgumentException("当前模型候选不存在、已过期或已经确认");
        }
        return PlatformDiscoveredModelCandidate.confirmedByAdministrator(modelCode, modelType);
    }

    private void recordInTransaction(PlatformProviderModelDiscovery discovery) {
        jdbc.update(
                "INSERT INTO model_runtime.platform_provider_model_discovery "
                        + "(id,platform_provider_connection_id,platform_provider_connection_revision,"
                        + "succeeded,result_code,elapsed_millis,discovered_at,expires_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                discovery.id().value(),
                discovery.connectionId().value(),
                discovery.connectionRevision(),
                discovery.succeeded(),
                discovery.resultCode(),
                discovery.elapsed().toMillis(),
                Timestamp.from(discovery.discoveredAt()),
                discovery.expiresAt() == null ? null : Timestamp.from(discovery.expiresAt()));
        for (var model : discovery.models()) {
            jdbc.update(
                    "INSERT INTO model_runtime.platform_discovered_model "
                            + "(discovery_id,model_code,model_type,capabilities,classification) "
                            + "VALUES (?,?,?,string_to_array(?,','),?)",
                    discovery.id().value(),
                    model.modelCode(),
                    model.modelType() == null ? null : model.modelType().name(),
                    model.capabilities().stream()
                            .map(Enum::name)
                            .sorted()
                            .collect(java.util.stream.Collectors.joining(",")),
                    model.classification().name());
        }
    }
}
