package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.repository;

import com.aiplatform.foundation.modelruntime.application.provider.port.out.ProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderModelDiscovery;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL 在一个事务内写入发现根和候选明细。 */
public final class PostgresProviderModelDiscoveryStore implements ProviderModelDiscoveryStore {

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
