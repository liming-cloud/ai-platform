package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformProviderModelDiscoveryStore;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderModelDiscovery;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL 在同一事务内写入平台模型发现根与候选。 */
public final class PostgresPlatformProviderModelDiscoveryStore
        implements PlatformProviderModelDiscoveryStore {

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
