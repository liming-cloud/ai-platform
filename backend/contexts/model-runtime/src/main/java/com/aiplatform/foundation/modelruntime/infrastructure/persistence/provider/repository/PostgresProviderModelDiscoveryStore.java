package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository;

import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ProviderModelDiscoveryLookup;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability;
import com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelType;
import com.aiplatform.foundation.modelruntime.domain.provider.model.DiscoveredModel;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscovery;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL 在一个事务内写入发现根和候选明细。 */
public final class PostgresProviderModelDiscoveryStore
        implements ProviderModelDiscoveryStore, ProviderModelDiscoveryLookup {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresProviderModelDiscoveryStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 模板不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public void record(ProviderModelDiscovery discovery) {
        discovery = Objects.requireNonNull(discovery, "模型发现事实不能为空");
        var captured = discovery;
        transactions.executeWithoutResult(status -> recordInTransaction(captured));
    }

    @Override
    public Optional<DiscoveredModel> findCurrentCandidate(
            TenantId tenantId,
            ProviderConnectionId providerConnectionId,
            long providerConnectionRevision,
            String modelCode,
            ModelType modelType,
            Instant now) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(providerConnectionId, "供应商连接标识不能为空");
        Objects.requireNonNull(modelCode, "模型代码不能为空");
        Objects.requireNonNull(modelType, "模型类型不能为空");
        Objects.requireNonNull(now, "当前时间不能为空");
        if (providerConnectionRevision < 0) {
            throw new IllegalArgumentException("供应商连接修订不能为负数");
        }
        return jdbc.query(
                "SELECT model.model_code, model.model_type, model.capabilities "
                        + "FROM model_runtime.provider_model_discovery discovery "
                        + "JOIN model_runtime.provider_discovered_model model "
                        + "ON model.discovery_id = discovery.id "
                        + "WHERE discovery.tenant_id = ? "
                        + "AND discovery.provider_connection_id = ? "
                        + "AND discovery.provider_connection_revision = ? "
                        + "AND discovery.succeeded = TRUE "
                        + "AND discovery.expires_at > ? "
                        + "AND model.model_code = ? AND model.model_type = ? "
                        + "ORDER BY discovery.discovered_at DESC LIMIT 1",
                resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.<DiscoveredModel>empty();
                    }
                    var capabilities = Arrays.stream(
                                    (Object[]) resultSet.getArray("capabilities").getArray())
                            .map(Object::toString)
                            .map(ModelCapability::valueOf)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet());
                    return Optional.of(new DiscoveredModel(
                            resultSet.getString("model_code"),
                            ModelType.valueOf(resultSet.getString("model_type")),
                            capabilities));
                },
                tenantId.value(),
                providerConnectionId.value(),
                providerConnectionRevision,
                Timestamp.from(now),
                modelCode,
                modelType.name());
    }

    private void recordInTransaction(ProviderModelDiscovery discovery) {
        jdbc.update(
                "INSERT INTO model_runtime.provider_model_discovery "
                        + "(id,tenant_id,provider_connection_id,provider_connection_revision,"
                        + "succeeded,result_code,elapsed_millis,discovered_at,expires_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                discovery.id().value(),
                discovery.tenantId().value(),
                discovery.providerConnectionId().value(),
                discovery.providerConnectionRevision(),
                discovery.succeeded(),
                discovery.resultCode(),
                discovery.elapsed().toMillis(),
                Timestamp.from(discovery.discoveredAt()),
                discovery.expiresAt() == null ? null : Timestamp.from(discovery.expiresAt()));
        for (var model : discovery.models()) {
            jdbc.update(
                    "INSERT INTO model_runtime.provider_discovered_model "
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
