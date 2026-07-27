package com.aiplatform.foundation.trustedquery.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.trustedquery.application.QueryQuotaExceededException;
import com.aiplatform.foundation.trustedquery.application.QueryQuotaLimitType;
import com.aiplatform.foundation.trustedquery.application.QueryRunConcurrentModificationException;
import com.aiplatform.foundation.trustedquery.application.QueryRunIdempotencyConflictException;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryQuotaPolicy;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaim;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.QueryQuotaSnapshot;
import com.aiplatform.foundation.trustedquery.domain.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.QueryRunEventType;
import com.aiplatform.foundation.trustedquery.domain.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.QueryRunStatus;
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
public final class PostgresQueryRunRepository implements QueryRunRepository {

    private final QueryRunMapper mapper;
    private final TransactionTemplate transactions;

    public PostgresQueryRunRepository(
            QueryRunMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.mapper = Objects.requireNonNull(mapper, "查询运行映射器不能为空");
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
    public void complete(QueryRun queryRun, long expectedVersion) {
        Objects.requireNonNull(queryRun, "查询运行不能为空");
        if (queryRun.status() == QueryRunStatus.RUNNING) {
            throw new IllegalArgumentException("不能把运行中状态作为终态提交");
        }
        var result = transactions.execute(status -> {
            var usage = queryRun.usage();
            var updated = mapper.completeRun(
                    queryRun.tenantId().value(),
                    queryRun.id().value(),
                    queryRun.status().name(),
                    queryRun.answer(),
                    queryRun.refusalReason() == null
                            ? null
                            : queryRun.refusalReason().name(),
                    usage.inputTokens(),
                    usage.outputTokens(),
                    usage.totalTokens(),
                    queryRun.version(),
                    queryRun.completedAt(),
                    expectedVersion);
            if (updated != 1) {
                throw new QueryRunConcurrentModificationException();
            }
            for (var citation : queryRun.citations()) {
                insertCitation(queryRun, citation);
            }
            insertEvent(QueryRunEvent.completed(queryRun, 2));
            return Boolean.TRUE;
        });
        Objects.requireNonNull(result, "查询完成事务没有返回结果");
    }

    @Override
    public Optional<QueryRun> find(TenantId tenantId, QueryRunId queryRunId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        var row = mapper.findRun(tenantId.value(), queryRunId.value());
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
        var rows = mapper.findEvents(
                tenantId.value(),
                queryRunId.value(),
                afterSequence,
                limit);
        var citations = rows.stream()
                .anyMatch(row -> QueryRunStatus.ANSWERED.name().equals(row.getStatus()))
                ? mapper.findCitations(tenantId.value(), queryRunId.value())
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
        var request = mapper.findRequest(tenantId, keyHash);
        if (request != null) {
            if (!requestFingerprint.equals(request.getRequestFingerprint())) {
                throw new QueryRunIdempotencyConflictException();
            }
            var replayed = mapper.findRun(
                    tenantId,
                    Long.parseLong(request.getQueryRunId()));
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
        var dailyCount = mapper.countAcceptedInPeriod(
                tenantId,
                proposedQuota.periodStart(),
                proposedQuota.periodEnd());
        if (dailyCount >= quota.dailyQueryLimit()) {
            throw new QueryQuotaExceededException(
                    QueryQuotaLimitType.DAILY,
                    quota.dailyQueryLimit(),
                    proposedQuota.periodEnd());
        }
        var activeLeases = mapper.countActiveLeases(
                tenantId,
                queryRun.createdAt());
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
        mapper.insertRun(
                tenantId,
                accepted.id().value(),
                accepted.assistantVersionId(),
                accepted.requestedBy().value(),
                accepted.questionDigest(),
                accepted.quota().periodStart(),
                accepted.quota().periodEnd(),
                accepted.quota().dailyLimit(),
                accepted.quota().dailyOrdinal(),
                accepted.quota().concurrentLimit(),
                accepted.quota().acceptedLeaseUntil(),
                accepted.createdAt());
        mapper.insertRequest(
                tenantId,
                keyHash,
                requestFingerprint,
                accepted.id().value(),
                accepted.createdAt());
        insertEvent(QueryRunEvent.started(accepted));
        return new QueryRunClaim(accepted, QueryRunClaimState.CLAIMED);
    }

    private QueryRun toRun(QueryRunRow row) {
        var tenantId = TenantId.parse(row.getTenantId());
        var queryRunId = QueryRunId.parse(row.getId());
        return QueryRun.reconstitute(
                tenantId,
                queryRunId,
                Long.parseLong(row.getAssistantVersionId()),
                MembershipId.parse(row.getRequestedBy()),
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
                mapper.findCitations(tenantId.value(), queryRunId.value())
                        .stream()
                        .map(PostgresQueryRunRepository::toCitation)
                        .toList(),
                new ModelUsage(
                        row.getInputTokens(),
                        row.getOutputTokens(),
                        row.getTotalTokens()),
                row.getVersion(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }

    private void insertCitation(QueryRun queryRun, QueryCitation citation) {
        mapper.insertCitation(
                queryRun.tenantId().value(),
                queryRun.id().value(),
                citation.sequence(),
                citation.dataSourceId(),
                citation.contentVersion(),
                citation.chunkId(),
                citation.ordinal(),
                citation.quote(),
                citation.score());
    }

    private void insertEvent(QueryRunEvent event) {
        var usage = event.usage();
        mapper.insertEvent(
                event.tenantId().value(),
                event.queryRunId().value(),
                event.sequence(),
                event.type().name(),
                event.status().name(),
                event.answer(),
                event.refusalReason() == null
                        ? null
                        : event.refusalReason().name(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                event.occurredAt());
    }

    private static QueryRunEvent toEvent(
            QueryRunEventRow row,
            List<QueryCitation> citations) {
        return QueryRunEvent.reconstitute(
                TenantId.parse(row.getTenantId()),
                QueryRunId.parse(row.getQueryRunId()),
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
                new ModelUsage(
                        row.getInputTokens(),
                        row.getOutputTokens(),
                        row.getTotalTokens()),
                row.getOccurredAt());
    }

    private static QueryCitation toCitation(QueryCitationRow row) {
        return new QueryCitation(
                row.getSequence(),
                Long.parseLong(row.getDataSourceId()),
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
