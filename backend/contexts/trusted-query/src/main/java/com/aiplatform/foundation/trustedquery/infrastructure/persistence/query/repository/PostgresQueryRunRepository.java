package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository;

import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryDiagnosticFactMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryCitationMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunEventMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryRunRequestMapper;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryCitationEntity;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryRunEntity;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryRunEventEntity;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryRunRequestEntity;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaExceededException;
import com.aiplatform.foundation.trustedquery.application.diagnostic.dto.QueryRunTerminalCommit;
import com.aiplatform.foundation.trustedquery.application.diagnostic.port.out.QueryRunTerminalCommitter;
import com.aiplatform.foundation.trustedquery.application.quota.QueryQuotaLimitType;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunConcurrentModificationException;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunIdempotencyConflictException;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaim;
import com.aiplatform.foundation.trustedquery.domain.query.policy.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryModelUsage;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryQuotaSnapshot;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRequesterId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunEventType;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用短事务占位避免相同幂等请求重复调用外部模型。
 */
public final class PostgresQueryRunRepository implements QueryRunRepository, QueryRunTerminalCommitter {

    private final QueryRunMapper mapper;
    private final QueryDiagnosticFactMapper diagnosticMapper;
    private final QueryRunRequestMapper requestMapper;
    private final QueryCitationMapper citationMapper;
    private final QueryRunEventMapper eventMapper;
    private final EntityIdGenerator identifiers;
    private final TransactionTemplate transactions;

    public PostgresQueryRunRepository(
            QueryRunMapper mapper,
            QueryDiagnosticFactMapper diagnosticMapper,
            QueryRunRequestMapper requestMapper,
            QueryCitationMapper citationMapper,
            QueryRunEventMapper eventMapper,
            EntityIdGenerator identifiers,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "查询运行映射器不能为空");
        this.diagnosticMapper = Objects.requireNonNull(diagnosticMapper, "查询诊断映射器不能为空");
        this.requestMapper = Objects.requireNonNull(
                requestMapper,
                "查询幂等请求映射器不能为空");
        this.citationMapper = Objects.requireNonNull(
                citationMapper,
                "查询引用映射器不能为空");
        this.eventMapper = Objects.requireNonNull(
                eventMapper,
                "查询事件映射器不能为空");
        this.identifiers = Objects.requireNonNull(identifiers, "标识生成器不能为空");
        transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "事务管理器不能为空"));
    }

    @Override
    public QueryRunClaim claim(
            QueryRun queryRun,
            String idempotencyKey,
            String requestFingerprint,
            QueryQuotaPolicy quota) {
        Objects.requireNonNull(queryRun, "查询运行不能为空");
        Objects.requireNonNull(quota, "查询配额策略不能为空");
        requireDigest(requestFingerprint, "请求指纹");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        var result = transactions.execute(status -> claimInTransaction(
                queryRun,
                sha256(idempotencyKey),
                requestFingerprint,
                quota));
        return Objects.requireNonNull(result, "查询占位事务没有返回结果");
    }

    @Override
    public void commit(QueryRunTerminalCommit terminal) {
        Objects.requireNonNull(terminal, "查询终态提交不能为空");
        var result = transactions.execute(status -> {
            completeInTransaction(terminal.queryRun(), terminal.expectedVersion());
            var fact = terminal.diagnostic();
            if (diagnosticMapper.insert(
                    terminal.queryRun().tenantId().value(), terminal.queryRun().id().value(),
                    fact.candidateCollection().name(), fact.candidateCount(),
                    fact.retrievalAuthorization().name(), fact.chatModelRevision(),
                    fact.embeddingModelRevision(), fact.citationValidation().name(),
                    terminal.queryRun().completedAt()) != 1) {
                throw new IllegalStateException("查询诊断事实写入失败");
            }
            return Boolean.TRUE;
        });
        Objects.requireNonNull(result, "查询终态诊断提交事务没有返回结果");
    }

    private void completeInTransaction(QueryRun queryRun, long expectedVersion) {
        if (queryRun.status() == QueryRunStatus.RUNNING) {
            throw new IllegalArgumentException("不能把运行中状态作为终态提交");
        }
        var usage = queryRun.usage();
        var updated = mapper.completeRun(queryRun.tenantId().value(), queryRun.id().value(), queryRun.status().name(),
                queryRun.answer(), queryRun.refusalReason() == null ? null : queryRun.refusalReason().name(),
                usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), queryRun.version(),
                queryRun.completedAt(), expectedVersion);
        if (updated != 1) {
            throw new QueryRunConcurrentModificationException();
        }
        for (var citation : queryRun.citations()) {
            insertCitation(queryRun, citation);
        }
        insertEvent(QueryRunEvent.completed(queryRun, 2));
    }

    @Override
    public Optional<QueryRun> find(TenantId tenantId, QueryRunId queryRunId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        var row = mapper.selectOne(runQuery(tenantId.value(), queryRunId.value()));
        return row == null ? Optional.empty() : Optional.of(toRun(row));
    }

    @Override
    public List<QueryRunEvent> findEvents(
            TenantId tenantId,
            QueryRunId queryRunId,
            long afterSequence,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        if (afterSequence < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("查询事件游标或数量不正确");
        }
        var rows = eventMapper.findAfter(
                tenantId.value(),
                queryRunId.value(),
                afterSequence,
                limit);
        var citations = rows.stream()
                .anyMatch(row -> QueryRunStatus.ANSWERED.name().equals(row.getStatus()))
                ? findCitations(tenantId.value(), queryRunId.value())
                        .stream()
                        .map(PostgresQueryRunRepository::toCitation)
                        .toList()
                : List.<QueryCitation>of();
        return rows.stream()
                .map(row -> toEvent(row, citations))
                .toList();
    }

    private QueryRunClaim claimInTransaction(
            QueryRun queryRun,
            String keyHash,
            String requestFingerprint,
            QueryQuotaPolicy quota) {
        var tenantId = queryRun.tenantId().value();
        // 同一租户的幂等判断、计数和插入必须串行，避免并发请求共同越过限额。
        mapper.lockScope("query-quota:" + tenantId);
        var request = requestMapper.selectOne(
                Wrappers.<QueryRunRequestEntity>lambdaQuery()
                        .eq(QueryRunRequestEntity::getTenantId, tenantId)
                        .eq(QueryRunRequestEntity::getIdempotencyKeyHash, keyHash));
        if (request != null) {
            if (!requestFingerprint.equals(request.getRequestFingerprint())) {
                throw new QueryRunIdempotencyConflictException();
            }
            var replayed = mapper.selectOne(
                    runQuery(tenantId, request.getQueryRunId()));
            if (replayed == null) {
                throw new IllegalStateException("查询幂等记录引用的运行不存在");
            }
            var restored = toRun(replayed);
            return new QueryRunClaim(
                    restored,
                    restored.status() == QueryRunStatus.RUNNING
                            ? QueryRunClaimState.IN_PROGRESS
                            : QueryRunClaimState.REPLAYED);
        }
        var proposedQuota = QueryQuotaSnapshot.forAcceptedQuery(
                queryRun.createdAt(),
                quota.dailyQueryLimit(),
                1,
                quota.concurrentQueryLimit(),
                quota.runningLease());
        var dailyCount = Math.toIntExact(mapper.selectCount(
                Wrappers.<QueryRunEntity>lambdaQuery()
                        .eq(QueryRunEntity::getTenantId, tenantId)
                        .ge(
                                QueryRunEntity::getCreatedAt,
                                proposedQuota.periodStart())
                        .lt(
                                QueryRunEntity::getCreatedAt,
                                proposedQuota.periodEnd())));
        if (dailyCount >= quota.dailyQueryLimit()) {
            throw new QueryQuotaExceededException(
                    QueryQuotaLimitType.DAILY,
                    quota.dailyQueryLimit(),
                    proposedQuota.periodEnd());
        }
        var activeLeases = Math.toIntExact(mapper.selectCount(
                Wrappers.<QueryRunEntity>lambdaQuery()
                        .eq(QueryRunEntity::getTenantId, tenantId)
                        .eq(QueryRunEntity::getStatus, QueryRunStatus.RUNNING.name())
                        .gt(
                                QueryRunEntity::getRunningLeaseUntil,
                                queryRun.createdAt())));
        if (activeLeases >= quota.concurrentQueryLimit()) {
            var retryAt = mapper.findEarliestActiveLeaseExpiry(
                    tenantId,
                    queryRun.createdAt());
            throw new QueryQuotaExceededException(
                    QueryQuotaLimitType.CONCURRENT,
                    quota.concurrentQueryLimit(),
                    Objects.requireNonNull(retryAt, "有效运行租约缺少截止时间"));
        }
        var accepted = queryRun.acceptQuota(QueryQuotaSnapshot.forAcceptedQuery(
                queryRun.createdAt(),
                quota.dailyQueryLimit(),
                dailyCount + 1,
                quota.concurrentQueryLimit(),
                quota.runningLease()));
        mapper.insert(toEntity(accepted));
        requestMapper.insert(toRequestEntity(accepted, keyHash, requestFingerprint));
        insertEvent(QueryRunEvent.started(accepted));
        return new QueryRunClaim(accepted, QueryRunClaimState.CLAIMED);
    }

    private QueryRun toRun(QueryRunEntity row) {
        var tenantId = new TenantId(row.getTenantId());
        var queryRunId = new QueryRunId(row.getId());
        return QueryRun.reconstitute(
                tenantId,
                queryRunId,
                row.getAssistantVersionId(),
                new QueryRequesterId(row.getRequestedBy()),
                row.getQuestionDigest(),
                new QueryQuotaSnapshot(
                        row.getQuotaPeriodStart(),
                        row.getQuotaPeriodEnd(),
                        row.getDailyQueryLimit(),
                        row.getDailyQueryOrdinal(),
                        row.getConcurrentQueryLimit(),
                        row.getAcceptedLeaseUntil()),
                QueryRunStatus.valueOf(row.getStatus()),
                row.getAnswer(),
                row.getRefusalReason() == null
                        ? null
                        : QueryRefusalReason.valueOf(row.getRefusalReason()),
                findCitations(tenantId.value(), queryRunId.value())
                        .stream()
                        .map(PostgresQueryRunRepository::toCitation)
                        .toList(),
                new QueryModelUsage(
                        row.getInputTokens(),
                        row.getOutputTokens(),
                        row.getTotalTokens()),
                row.getVersion(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }

    private List<QueryCitationEntity> findCitations(
            long tenantId,
            long queryRunId) {
        return citationMapper.selectList(
                Wrappers.<QueryCitationEntity>lambdaQuery()
                        .eq(QueryCitationEntity::getTenantId, tenantId)
                        .eq(QueryCitationEntity::getQueryRunId, queryRunId)
                        .orderByAsc(QueryCitationEntity::getSequence));
    }

    private static com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
            QueryRunEntity> runQuery(long tenantId, long queryRunId) {
        return Wrappers.<QueryRunEntity>lambdaQuery()
                .eq(QueryRunEntity::getTenantId, tenantId)
                .eq(QueryRunEntity::getId, queryRunId);
    }

    private static QueryRunEntity toEntity(QueryRun run) {
        var entity = new QueryRunEntity();
        entity.setId(run.id().value());
        entity.setTenantId(run.tenantId().value());
        entity.setAssistantVersionId(run.assistantVersionId());
        entity.setRequestedBy(run.requestedBy().value());
        entity.setQuestionDigest(run.questionDigest());
        entity.setQuotaPeriodStart(run.quota().periodStart());
        entity.setQuotaPeriodEnd(run.quota().periodEnd());
        entity.setDailyQueryLimit(run.quota().dailyLimit());
        entity.setDailyQueryOrdinal(run.quota().dailyOrdinal());
        entity.setConcurrentQueryLimit(run.quota().concurrentLimit());
        entity.setAcceptedLeaseUntil(run.quota().acceptedLeaseUntil());
        entity.setRunningLeaseUntil(run.quota().acceptedLeaseUntil());
        entity.setStatus(run.status().name());
        entity.setVersion(run.version());
        entity.setCreatedAt(run.createdAt());
        return entity;
    }

    private static QueryRunRequestEntity toRequestEntity(
            QueryRun run,
            String keyHash,
            String fingerprint) {
        var entity = new QueryRunRequestEntity();
        entity.setId(run.id().value());
        entity.setTenantId(run.tenantId().value());
        entity.setIdempotencyKeyHash(keyHash);
        entity.setRequestFingerprint(fingerprint);
        entity.setQueryRunId(run.id().value());
        entity.setCreatedAt(run.createdAt());
        return entity;
    }

    private void insertCitation(QueryRun queryRun, QueryCitation citation) {
        var entity = new QueryCitationEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(queryRun.tenantId().value());
        entity.setQueryRunId(queryRun.id().value());
        entity.setSequence(citation.sequence());
        entity.setDataSourceId(citation.dataSourceId());
        entity.setContentVersion(citation.contentVersion());
        entity.setChunkId(citation.chunkId());
        entity.setOrdinal(citation.ordinal());
        entity.setQuote(citation.quote());
        entity.setScore(citation.score());
        citationMapper.insert(entity);
    }

    private void insertEvent(QueryRunEvent event) {
        var usage = event.usage();
        var entity = new QueryRunEventEntity();
        entity.setId(identifiers.nextId());
        entity.setTenantId(event.tenantId().value());
        entity.setQueryRunId(event.queryRunId().value());
        entity.setSequence(event.sequence());
        entity.setEventType(event.type().name());
        entity.setStatus(event.status().name());
        entity.setAnswer(event.answer());
        entity.setRefusalReason(event.refusalReason() == null
                ? null
                : event.refusalReason().name());
        entity.setInputTokens(usage.inputTokens());
        entity.setOutputTokens(usage.outputTokens());
        entity.setTotalTokens(usage.totalTokens());
        entity.setOccurredAt(event.occurredAt());
        eventMapper.insert(entity);
    }

    private static QueryRunEvent toEvent(
            QueryRunEventEntity row,
            List<QueryCitation> citations) {
        return QueryRunEvent.reconstitute(
                new TenantId(row.getTenantId()),
                new QueryRunId(row.getQueryRunId()),
                row.getSequence(),
                QueryRunEventType.valueOf(row.getEventType()),
                QueryRunStatus.valueOf(row.getStatus()),
                row.getAnswer(),
                row.getRefusalReason() == null
                        ? null
                        : QueryRefusalReason.valueOf(row.getRefusalReason()),
                QueryRunStatus.ANSWERED.name().equals(row.getStatus())
                        ? citations
                        : List.of(),
                new QueryModelUsage(
                        row.getInputTokens(),
                        row.getOutputTokens(),
                        row.getTotalTokens()),
                row.getOccurredAt());
    }

    private static QueryCitation toCitation(QueryCitationEntity row) {
        return new QueryCitation(
                row.getSequence(),
                row.getDataSourceId(),
                row.getContentVersion(),
                row.getChunkId(),
                row.getOrdinal(),
                row.getQuote(),
                row.getScore());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + "格式不正确");
        }
    }
}
