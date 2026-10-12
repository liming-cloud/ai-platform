package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionConcurrentModificationException;
import com.aiplatform.foundation.modelruntime.application.provider.exception.ProviderConnectionNotFoundException;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.publication.PlatformProviderConnectionPublication;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionPublicationRepository;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.repository.PlatformProviderConnectionRepository;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity.PlatformProviderConnectionPublicationEntity;
import com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper.PlatformProviderConnectionPublicationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 同一连接行锁内复验并提交发布与操作者事实；不持有网络调用或任何秘密。 */
public final class PostgresPlatformProviderConnectionPublicationRepository implements PlatformProviderConnectionPublicationRepository {
    private final PlatformProviderConnectionPublicationMapper mapper;
    private final PlatformProviderConnectionRepository connections;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public PostgresPlatformProviderConnectionPublicationRepository(PlatformProviderConnectionPublicationMapper mapper,
            PlatformProviderConnectionRepository connections, PlatformTransactionManager transactions, Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "发布映射器不能为空");
        this.connections = Objects.requireNonNull(connections, "连接仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactions, "事务管理器不能为空"));
    }

    @Override
    public PlatformProviderConnectionPublication publish(PlatformProviderConnectionId id, long expectedVersion,
            long publicationId, long actor, Duration validity) {
        return Objects.requireNonNull(transactions.execute(status -> {
            if (mapper.lockConnection(id.value()) == null) {
                throw new ProviderConnectionNotFoundException();
            }
            var connection = connections.findById(id).orElseThrow(ProviderConnectionNotFoundException::new);
            if (connection.version() != expectedVersion) {
                throw new ProviderConnectionConcurrentModificationException();
            }
            // 先复验当前探测再处理重放；历史发布不延长过期探测的有效期。
            var latest = mapper.latestRevision(id.value());
            var publication = PlatformProviderConnectionPublication.publish(publicationId,
                    latest == null ? 1L : latest + 1L, connection, actor, clock.instant(), validity);
            var existing = find(id, expectedVersion);
            if (existing.isPresent()) {
                return existing.get();
            }
            if (mapper.insert(toEntity(publication)) != 1) {
                throw new IllegalStateException("连接发布事实提交失败");
            }
            // 数据库会规整时间精度；首次响应和重放都读取同一条真实持久化事实。
            return find(id, expectedVersion).orElseThrow(() -> new IllegalStateException("连接发布事实读取失败"));
        }), "连接发布事务没有返回结果");
    }

    @Override
    public Optional<PlatformProviderConnectionPublication> find(PlatformProviderConnectionId id, long connectionRevision) {
        var row = mapper.selectOne(Wrappers.<PlatformProviderConnectionPublicationEntity>lambdaQuery()
                .eq(PlatformProviderConnectionPublicationEntity::getConnectionId, id.value())
                .eq(PlatformProviderConnectionPublicationEntity::getConnectionRevision, connectionRevision));
        return Optional.ofNullable(row).map(PostgresPlatformProviderConnectionPublicationRepository::toDomain);
    }

    private static PlatformProviderConnectionPublicationEntity toEntity(PlatformProviderConnectionPublication publication) {
        var row = new PlatformProviderConnectionPublicationEntity();
        row.setId(publication.id());
        row.setConnectionId(publication.connectionId().value());
        row.setRevision(publication.revision());
        row.setConnectionRevision(publication.connectionRevision());
        row.setPublishedBySubjectId(publication.publishedBySubjectId());
        row.setProbedAt(publication.probedAt());
        row.setPublishedAt(publication.publishedAt());
        return row;
    }

    private static PlatformProviderConnectionPublication toDomain(PlatformProviderConnectionPublicationEntity row) {
        return new PlatformProviderConnectionPublication(row.getId(), new PlatformProviderConnectionId(row.getConnectionId()),
                row.getRevision(), row.getConnectionRevision(), row.getPublishedBySubjectId(), row.getProbedAt(), row.getPublishedAt());
    }
}
