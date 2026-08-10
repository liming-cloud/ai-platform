package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.repository;

import com.aiplatform.foundation.modelruntime.application.deployment.port.out.ModelDeploymentVerificationStore;
import com.aiplatform.foundation.modelruntime.domain.deployment.operation.ModelDeploymentVerificationResult;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelCapabilityVerificationMapper;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.mapper.ModelDeploymentMapper;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL 原子写入验证事实和部署当前验证引用，不补写领域判断。
 */
public final class PostgresModelDeploymentVerificationStore
        implements ModelDeploymentVerificationStore {

    private final ModelDeploymentMapper deployments;
    private final ModelCapabilityVerificationMapper verifications;
    private final TransactionTemplate transactions;

    public PostgresModelDeploymentVerificationStore(
            ModelDeploymentMapper deployments,
            ModelCapabilityVerificationMapper verifications,
            PlatformTransactionManager transactionManager) {
        this.deployments = Objects.requireNonNull(deployments, "模型部署映射器不能为空");
        this.verifications = Objects.requireNonNull(verifications, "模型能力验证映射器不能为空");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public void commit(ModelDeploymentVerificationResult result, long expectedDeploymentVersion) {
        result = Objects.requireNonNull(result, "模型能力验证结果不能为空");
        if (expectedDeploymentVersion < 0) {
            throw new IllegalArgumentException("期望模型部署版本不能为负数");
        }
        var captured = result;
        transactions.executeWithoutResult(status -> commitInTransaction(captured, expectedDeploymentVersion));
    }

    private void commitInTransaction(
            ModelDeploymentVerificationResult result,
            long expectedDeploymentVersion) {
        var deployment = result.revisedDeployment();
        var locked = deployments.lockById(deployment.tenantId().value(), deployment.id().value());
        if (locked == null || locked.getVersion() != expectedDeploymentVersion) {
            throw new IllegalStateException("模型部署版本已改变");
        }
        var verification = result.verification();
        if (verification.deploymentVersion() != expectedDeploymentVersion) {
            throw new IllegalStateException("模型能力验证引用了过时部署修订");
        }
        requireSingleWrite(verifications.insert(
                verification.id().value(),
                verification.tenantId().value(),
                verification.deploymentId().value(),
                verification.deploymentVersion(),
                verification.providerConnectionId().value(),
                verification.providerConnectionRevision(),
                verification.modelCode(),
                capabilities(verification.declaredCapabilities()),
                capabilities(verification.observedCapabilities()),
                verification.succeeded(),
                verification.resultCode(),
                verification.elapsed().toMillis(),
                verification.probedAt(),
                verification.expiresAt()), "模型能力验证事实写入失败");
        if (result.verified()) {
            requireSingleWrite(deployments.markVerified(
                    deployment.tenantId().value(),
                    deployment.id().value(),
                    deployment.lastVerificationId().value(),
                    deployment.verificationExpiresAt(),
                    deployment.version(),
                    expectedDeploymentVersion), "模型部署验证状态写入失败");
        }
    }

    private static String capabilities(java.util.Set<com.aiplatform.foundation.modelruntime.domain.deployment.model.ModelCapability> values) {
        return values.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private static void requireSingleWrite(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
