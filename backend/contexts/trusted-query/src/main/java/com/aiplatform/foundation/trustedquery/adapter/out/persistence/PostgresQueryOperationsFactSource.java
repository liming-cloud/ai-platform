package com.aiplatform.foundation.trustedquery.adapter.out.persistence;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.organizationaccess.api.model.MembershipId;
import com.aiplatform.foundation.trustedquery.api.operations.QueryAuditFact;
import com.aiplatform.foundation.trustedquery.api.operations.QueryAuditFactPage;
import com.aiplatform.foundation.trustedquery.api.operations.QueryOperationsFactSource;
import com.aiplatform.foundation.trustedquery.api.operations.QueryUsageFactQuery;
import com.aiplatform.foundation.trustedquery.api.operations.QueryUsageFacts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * 从查询运行唯一事实生成运营投影，数据库选择列禁止包含敏感正文。
 */
public final class PostgresQueryOperationsFactSource
        implements QueryOperationsFactSource {

    private static final int MAX_CURSOR_LENGTH = 512;

    private final QueryRunMapper mapper;

    public PostgresQueryOperationsFactSource(QueryRunMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "查询运行映射器不能为空");
    }

    @Override
    public QueryUsageFacts summarize(QueryUsageFactQuery query) {
        Objects.requireNonNull(query, "查询用量事实请求不能为空");
        var row = mapper.summarizeUsage(
                query.tenantId().value(),
                query.from(),
                query.to(),
                query.currentPeriodStart(),
                query.currentPeriodEnd(),
                query.observedAt());
        if (row == null) {
            throw new IllegalStateException("查询用量汇总没有返回结果");
        }
        return new QueryUsageFacts(
                row.getAcceptedQueries(),
                row.getAnsweredQueries(),
                row.getRefusedQueries(),
                row.getFailedQueries(),
                row.getRunningQueries(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getTotalTokens(),
                row.getDailyUsed(),
                row.getConcurrentUsed());
    }

    @Override
    public QueryAuditFactPage listAuditRecords(
            TenantId tenantId,
            String cursor,
            int limit) {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("分页条数必须在 1 到 100 之间");
        }
        var fetchLimit = limit + 1;
        List<QueryAuditFactRow> rows;
        if (cursor == null) {
            rows = mapper.findFirstAuditPage(tenantId.value(), fetchLimit);
        } else {
            var position = decodeCursor(cursor);
            rows = mapper.findAuditAfter(
                    tenantId.value(),
                    position.createdAt(),
                    position.id(),
                    fetchLimit);
        }
        var hasNext = rows.size() > limit;
        var pageRows = hasNext ? rows.subList(0, limit) : rows;
        var items = pageRows.stream()
                .map(PostgresQueryOperationsFactSource::toFact)
                .toList();
        var nextCursor = hasNext
                ? encodeCursor(
                        pageRows.getLast().getCreatedAt(),
                        Long.parseLong(pageRows.getLast().getQueryRunId()))
                : null;
        return new QueryAuditFactPage(items, nextCursor);
    }

    private static QueryAuditFact toFact(QueryAuditFactRow row) {
        return new QueryAuditFact(
                row.getQueryRunId(),
                MembershipId.parse(row.getRequestedBy()),
                row.getAssistantVersionId(),
                row.getQuestionDigest(),
                row.getStatus(),
                row.getRefusalReason(),
                row.getInputTokens(),
                row.getOutputTokens(),
                row.getTotalTokens(),
                row.getCreatedAt(),
                row.getCompletedAt());
    }

    private static String encodeCursor(Instant createdAt, long id) {
        var payload = createdAt + "|" + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorPosition decodeCursor(String cursor) {
        if (cursor == null
                || cursor.isBlank()
                || cursor.length() > MAX_CURSOR_LENGTH
                || !cursor.equals(cursor.trim())) {
            throw new IllegalArgumentException("游标格式不正确");
        }
        try {
            var payload = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            var separator = payload.lastIndexOf('|');
            if (separator < 1 || separator == payload.length() - 1) {
                throw new IllegalArgumentException("游标格式不正确");
            }
            return new CursorPosition(
                    Instant.parse(payload.substring(0, separator)),
                    Long.parseLong(payload.substring(separator + 1)));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("游标格式不正确", exception);
        }
    }

    private record CursorPosition(Instant createdAt, long id) {
    }
}
