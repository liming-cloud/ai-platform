package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorBatch;
import com.aiplatform.foundation.knowledgedata.api.extension.IndexVectorWriter;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildCompletionPort;
import com.aiplatform.foundation.knowledgedata.application.port.out.IndexBuildLease;
import java.time.Instant;
import java.util.Objects;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在一个 PostgreSQL 事务中保存向量产物并完成构建状态变更。
 */
public final class PostgresIndexBuildCompletion
        implements IndexBuildCompletionPort {

    private final IndexVersionMapper mapper;
    private final IndexVectorWriter writer;
    private final TransactionTemplate transactions;

    public PostgresIndexBuildCompletion(
            IndexVersionMapper mapper,
            IndexVectorWriter writer,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "索引版本映射器不能为空");
        this.writer = Objects.requireNonNull(writer, "索引向量写入端口不能为空");
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public void complete(
            IndexBuildLease lease,
            IndexVectorBatch value,
            String digest,
            Instant completedAt) {
        Objects.requireNonNull(lease, "索引构建租约不能为空");
        Objects.requireNonNull(value, "索引向量批次不能为空");
        Objects.requireNonNull(digest, "索引构建摘要不能为空");
        Objects.requireNonNull(completedAt, "索引构建完成时间不能为空");
        validateBatch(lease, value);
        transactions.executeWithoutResult(status -> completeInTransaction(
                lease,
                value,
                digest,
                completedAt));
    }

    private void completeInTransaction(
            IndexBuildLease lease,
            IndexVectorBatch batch,
            String digest,
            Instant completedAt) {
        var index = lease.indexVersion();
        var tenantId = index.tenantId().value();
        var spaceId = index.knowledgeSpaceId().value();
        var indexId = index.id().value();
        if (mapper.findRunningLease(
                tenantId,
                spaceId,
                indexId,
                lease.leaseId()) == null) {
            throw new IndexVersionConcurrentModificationException();
        }
        writer.replace(batch);
        if (mapper.markReady(
                tenantId,
                spaceId,
                indexId,
                digest,
                batch.vectors().size(),
                completedAt) != 1
                || mapper.markDone(
                        tenantId,
                        spaceId,
                        indexId,
                        lease.leaseId(),
                        completedAt) != 1) {
            throw new IndexVersionConcurrentModificationException();
        }
    }

    private static void validateBatch(
            IndexBuildLease lease,
            IndexVectorBatch batch) {
        var index = lease.indexVersion();
        var embedding = index.embedding();
        if (!batch.tenantId().equals(index.tenantId().value())
                || !batch.knowledgeSpaceId().equals(index.knowledgeSpaceId().value())
                || !batch.indexVersionId().equals(index.id().value())
                || !batch.embeddingDeploymentId().equals(embedding.deploymentId())
                || batch.embeddingRevision() != embedding.deploymentRevision()
                || batch.embeddingDimension() != embedding.dimension()) {
            throw new IllegalArgumentException("索引向量批次与构建租约不一致");
        }
    }
}
