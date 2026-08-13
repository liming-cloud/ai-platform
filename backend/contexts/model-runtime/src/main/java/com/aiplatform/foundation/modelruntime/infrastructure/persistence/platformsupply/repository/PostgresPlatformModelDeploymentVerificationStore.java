package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.PlatformModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 在一个 PostgreSQL 事务中记录平台验证事实并乐观更新成功验证的部署。 */
public final class PostgresPlatformModelDeploymentVerificationStore implements PlatformModelDeploymentVerificationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public PostgresPlatformModelDeploymentVerificationStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC 模板不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }
    @Override public void commit(PlatformModelDeploymentVerificationResult result, long expectedDeploymentVersion) {
        result = Objects.requireNonNull(result, "平台模型验证结果不能为空");
        var captured = result;
        transactions.executeWithoutResult(status -> commitInTransaction(captured, expectedDeploymentVersion));
    }
    private void commitInTransaction(PlatformModelDeploymentVerificationResult result, long expectedVersion) {
        var verification = result.verification();
        jdbc.update("""
                INSERT INTO model_runtime.platform_model_capability_verification (
                    id, platform_model_deployment_id, deployment_revision, platform_provider_connection_id,
                    platform_provider_connection_revision, model_code, declared_capabilities, observed_capabilities,
                    succeeded, result_code, elapsed_millis, probed_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, string_to_array(?, ','), string_to_array(?, ','), ?, ?, ?, ?, ?)
                """, verification.id().value(), verification.deploymentId().value(), verification.deploymentRevision(),
                verification.providerConnectionId().value(), verification.providerConnectionRevision(), verification.modelCode(),
                capabilities(verification.declaredCapabilities()), capabilities(verification.observedCapabilities()), verification.succeeded(),
                verification.resultCode(), verification.elapsed().toMillis(), Timestamp.from(verification.probedAt()), timestamp(verification.expiresAt()));
        if (verification.succeeded() && jdbc.update("""
                UPDATE model_runtime.platform_model_deployment
                SET status = ?, last_verification_id = ?, verification_expires_at = ?, version = ?
                WHERE id = ? AND scope = 'PLATFORM' AND version = ?
                """, result.revisedDeployment().status().name(), verification.id().value(), timestamp(verification.expiresAt()),
                result.revisedDeployment().version(), result.revisedDeployment().id().value(), expectedVersion) != 1) {
            throw new IllegalStateException("平台模型部署修订已变化");
        }
    }
    private static String capabilities(java.util.Set<com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability> values) {
        return values.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
    }
    private static Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }
}
