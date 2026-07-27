package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.index;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionConcurrentModificationException;
import com.aiplatform.foundation.knowledgedata.application.indexversion.IndexVersionNotFoundException;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildLease;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexBuildQueuePort;
import com.aiplatform.foundation.knowledgedata.application.port.out.index.IndexSourceSnapshot;
import com.aiplatform.foundation.knowledgedata.domain.indexversion.IndexVersionId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextContentReference;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用行锁跳过已认领作业的 PostgreSQL 构建队列。
 */
public final class PostgresIndexBuildQueue implements IndexBuildQueuePort {

    private final IndexVersionMapper mapper;
    private final TransactionTemplate transactions;
    private final EntityIdGenerator identifiers;

    public PostgresIndexBuildQueue(
            IndexVersionMapper mapper,
            PlatformTransactionManager transactionManager,
            EntityIdGenerator identifiers) {
        this.mapper = Objects.requireNonNull(mapper, "索引版本映射器不能为空");
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
    }

    @Override
    public Optional<IndexBuildLease> claim(
            Instant now,
            Duration leaseDuration) {
        Objects.requireNonNull(now, "认领时间不能为空");
        Objects.requireNonNull(leaseDuration, "租约时长不能为空");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("租约时长必须大于零");
        }
        var result = transactions.execute(status -> claimInTransaction(
                now,
                leaseDuration));
        return Objects.requireNonNull(result, "索引认领事务没有返回结果");
    }

    @Override
    public void recordFailure(
            IndexBuildLease value,
            String code,
            Instant failedAt) {
        Objects.requireNonNull(value, "索引构建租约不能为空");
        Objects.requireNonNull(code, "构建失败码不能为空");
        Objects.requireNonNull(failedAt, "构建失败时间不能为空");
        transactions.executeWithoutResult(status -> failInTransaction(
                value,
                code,
                failedAt));
    }

    private Optional<IndexBuildLease> claimInTransaction(
            Instant now,
            Duration leaseDuration) {
        var job = mapper.claimableJob(now);
        if (job == null) {
            return Optional.empty();
        }
        var tenantId = Long.parseLong(job.getTenantId());
        var knowledgeSpaceId = Long.parseLong(job.getKnowledgeSpaceId());
        var indexVersionId = Long.parseLong(job.getIndexVersionId());
        var leaseId = identifiers.nextId();
        if (mapper.markClaimed(
                tenantId,
                knowledgeSpaceId,
                indexVersionId,
                leaseId,
                now.plus(leaseDuration)) != 1) {
            throw new IndexVersionConcurrentModificationException();
        }
        var versionRow = mapper.findVersion(
                tenantId,
                knowledgeSpaceId,
                indexVersionId);
        if (versionRow == null) {
            throw new IndexVersionNotFoundException();
        }
        var sources = mapper.findSources(
                        tenantId,
                        knowledgeSpaceId,
                        indexVersionId)
                .stream()
                .map(PostgresIndexBuildQueue::toSnapshot)
                .toList();
        if (sources.isEmpty()) {
            throw new IllegalStateException("索引构建作业缺少冻结来源");
        }
        return Optional.of(new IndexBuildLease(
                leaseId,
                PostgresIndexVersionRepository.toDomain(versionRow),
                sources,
                job.getAttempt() + 1));
    }

    private void failInTransaction(
            IndexBuildLease lease,
            String code,
            Instant failedAt) {
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
        if (mapper.markFailedVersion(
                tenantId,
                spaceId,
                indexId,
                code) != 1
                || mapper.markFailedJob(
                        tenantId,
                        spaceId,
                        indexId,
                        lease.leaseId(),
                        code,
                        failedAt) != 1) {
            throw new IndexVersionConcurrentModificationException();
        }
    }

    private static IndexSourceSnapshot toSnapshot(IndexSourceRow row) {
        return new IndexSourceSnapshot(
                DataSourceId.parse(row.getDataSourceId()),
                new TextContentReference(
                        row.getContentVersion(),
                        row.getObjectKey(),
                        row.getSha256(),
                        row.getByteSize(),
                        row.getMediaType(),
                        row.getContentCreatedAt()));
    }
}
