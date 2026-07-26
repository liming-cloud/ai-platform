package com.aiplatform.foundation.trustedquery.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.modelruntime.api.model.ModelUsage;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.trustedquery.application.QueryRunConcurrentModificationException;
import com.aiplatform.foundation.trustedquery.application.QueryRunIdempotencyConflictException;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaim;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunClaimState;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.QueryRefusalReason;
import com.aiplatform.foundation.trustedquery.domain.QueryRun;
import com.aiplatform.foundation.trustedquery.domain.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.QueryRunEventType;
import com.aiplatform.foundation.trustedquery.domain.QueryRunStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
            String requestFingerprint) {
        Objects.requireNonNull(queryRun, "查询运行不能为空");
        requireDigest(requestFingerprint, "请求指纹");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }
        var result = transactions.execute(status -> claimInTransaction(
                queryRun,
                sha256(idempotencyKey),
                requestFingerprint));
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
                    queryRun.id(),
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
    public Optional<QueryRun> find(TenantId tenantId, UUID queryRunId) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        var row = mapper.findRun(tenantId.value(), queryRunId);
        return row == null ? Optional.empty() : Optional.of(toRun(row));
    }

    @Override
    public List<QueryRunEvent> findEvents(
            TenantId tenantId,
            UUID queryRunId,
            long afterSequence,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(queryRunId, "查询运行标识不能为空");
        if (afterSequence < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("查询事件游标或数量不正确");
        }
        var rows = mapper.findEvents(
                tenantId.value(),
                queryRunId,
                afterSequence,
                limit);
        var citations = rows.stream()
                .anyMatch(row -> QueryRunStatus.ANSWERED.name().equals(row.getStatus()))
                ? mapper.findCitations(tenantId.value(), queryRunId)
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
            String requestFingerprint) {
        var tenantId = queryRun.tenantId().value();
        mapper.lockScope(tenantId + ":" + keyHash);
        var request = mapper.findRequest(tenantId, keyHash);
        if (request != null) {
            if (!requestFingerprint.equals(request.getRequestFingerprint())) {
                throw new QueryRunIdempotencyConflictException();
            }
            var replayed = mapper.findRun(
                    tenantId,
                    UUID.fromString(request.getQueryRunId()));
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
        mapper.insertRun(
                tenantId,
                queryRun.id(),
                queryRun.assistantVersionId(),
                queryRun.requestedBy().value(),
                queryRun.questionDigest(),
                queryRun.createdAt());
        mapper.insertRequest(
                tenantId,
                keyHash,
                requestFingerprint,
                queryRun.id(),
                queryRun.createdAt());
        insertEvent(QueryRunEvent.started(queryRun));
        return new QueryRunClaim(queryRun, QueryRunClaimState.CLAIMED);
    }

    private QueryRun toRun(QueryRunRow row) {
        var tenantId = TenantId.parse(row.getTenantId());
        var queryRunId = UUID.fromString(row.getId());
        return QueryRun.reconstitute(
                tenantId,
                queryRunId,
                UUID.fromString(row.getAssistantVersionId()),
                new MembershipId(UUID.fromString(row.getRequestedBy())),
                row.getQuestionDigest(),
                QueryRunStatus.valueOf(row.getStatus()),
                row.getAnswer(),
                row.getRefusalReason() == null
                        ? null
                        : QueryRefusalReason.valueOf(row.getRefusalReason()),
                mapper.findCitations(tenantId.value(), queryRunId)
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
                queryRun.id(),
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
                event.queryRunId(),
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
                UUID.fromString(row.getQueryRunId()),
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
                UUID.fromString(row.getDataSourceId()),
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
