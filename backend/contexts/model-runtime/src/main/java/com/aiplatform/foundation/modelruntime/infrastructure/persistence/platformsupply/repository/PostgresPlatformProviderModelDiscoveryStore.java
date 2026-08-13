package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
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

/** PostgreSQL 在同一事务内写入平台模型发现根与候选。 */
public final class PostgresPlatformProviderModelDiscoveryStore
        implements PlatformProviderModelDiscoveryStore, PlatformProviderModelDiscoveryLookup {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresPlatformProviderModelDiscoveryStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 模板不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public void record(PlatformProviderModelDiscovery discovery) {
        discovery = Objects.requireNonNull(discovery, "平台模型发现事实不能为空");
        var captured = discovery;
        transactions.executeWithoutResult(status -> recordInTransaction(captured));
    }

    @Override
    public Optional<DiscoveredModel> findCurrentCandidate(
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
                SELECT candidate.model_code, candidate.model_type,
                       array_to_string(candidate.capabilities, ',') AS capabilities
                FROM model_runtime.platform_provider_model_discovery discovery
                JOIN model_runtime.platform_discovered_model candidate ON candidate.discovery_id = discovery.id
                WHERE discovery.platform_provider_connection_id = ?
                  AND discovery.platform_provider_connection_revision = ?
                  AND discovery.succeeded = TRUE
                  AND discovery.expires_at > ?
                  AND candidate.model_code = ?
                  AND candidate.model_type = ?
                ORDER BY discovery.discovered_at DESC, discovery.id DESC
                LIMIT 1
                """, rs -> rs.next() ? new DiscoveredModel(
                        rs.getString("model_code"), ModelType.valueOf(rs.getString("model_type")),
                        Arrays.stream(rs.getString("capabilities").split(",", -1))
                                .map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet())) : null,
                connectionId.value(), connectionRevision, Timestamp.from(now), modelCode, modelType.name()));
    }

    @Override
    public PageResult<DiscoveredModel> findCurrentCandidates(
            PlatformProviderConnectionId connectionId,
            long connectionRevision,
            PageQuery page,
            Instant now) {
        connectionId = Objects.requireNonNull(connectionId, "平台供应商连接标识不能为空");
        page = Objects.requireNonNull(page, "分页查询不能为空");
        now = Objects.requireNonNull(now, "当前时间不能为空");
        if (connectionRevision < 0) throw new IllegalArgumentException("平台供应商连接修订不能为负数");
        var items = jdbc.query("""
                WITH current_discovery AS (
                    SELECT id FROM model_runtime.platform_provider_model_discovery
                    WHERE platform_provider_connection_id = ?
                      AND platform_provider_connection_revision = ?
                      AND succeeded = TRUE
                      AND expires_at > ?
                    ORDER BY discovered_at DESC, id DESC
                    LIMIT 1
                )
                SELECT model_code, model_type, array_to_string(capabilities, ',') AS capabilities
                FROM model_runtime.platform_discovered_model candidate
                JOIN current_discovery ON current_discovery.id = candidate.discovery_id
                ORDER BY candidate.model_code ASC, candidate.model_type ASC
                LIMIT ? OFFSET ?
                """, (rs, ignored) -> new DiscoveredModel(
                        rs.getString("model_code"), ModelType.valueOf(rs.getString("model_type")),
                        Arrays.stream(rs.getString("capabilities").split(",", -1))
                                .map(ModelCapability::valueOf).collect(Collectors.toUnmodifiableSet())),
                connectionId.value(), connectionRevision, Timestamp.from(now), page.pageSize(), page.offset());
        var total = jdbc.queryForObject("""
                WITH current_discovery AS (
                    SELECT id FROM model_runtime.platform_provider_model_discovery
                    WHERE platform_provider_connection_id = ?
                      AND platform_provider_connection_revision = ?
                      AND succeeded = TRUE
                      AND expires_at > ?
                    ORDER BY discovered_at DESC, id DESC
                    LIMIT 1
                )
                SELECT COUNT(*) FROM model_runtime.platform_discovered_model candidate
                JOIN current_discovery ON current_discovery.id = candidate.discovery_id
                """, Long.class, connectionId.value(), connectionRevision, Timestamp.from(now));
        return PageResult.of(items, page, total == null ? 0L : total);
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
                            + "(discovery_id,model_code,model_type,capabilities) "
                            + "VALUES (?,?,?,string_to_array(?,','))",
                    discovery.id().value(),
                    model.modelCode(),
                    model.modelType().name(),
                    model.capabilities().stream()
                            .map(Enum::name)
                            .sorted()
                            .collect(java.util.stream.Collectors.joining(",")));
        }
    }
}
