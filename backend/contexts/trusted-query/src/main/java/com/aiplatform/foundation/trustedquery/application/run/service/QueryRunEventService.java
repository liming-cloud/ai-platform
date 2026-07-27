package com.aiplatform.foundation.trustedquery.application.run.service;

import com.aiplatform.foundation.trustedquery.application.candidate.exception.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.trustedquery.application.run.exception.QueryRunNotFoundException;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.trustedquery.application.run.query.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryCitationView;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryRunEventPage;
import com.aiplatform.foundation.trustedquery.application.run.dto.QueryRunEventView;
import com.aiplatform.foundation.trustedquery.domain.query.repository.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunStatus;
import java.util.Objects;

/**
 * 在可信租户边界内按游标重放查询事件。
 */
public final class QueryRunEventService  {

    private static final PermissionCode QUERY = new PermissionCode("assistant:query");
    private static final PermissionCode KNOWLEDGE_READ =
            new PermissionCode("knowledge:read");

    private final QueryRunRepository repository;

    public QueryRunEventService(QueryRunRepository repository) {
        this.repository = Objects.requireNonNull(repository, "查询运行仓储不能为空");
    }

    public QueryRunEventPage list(ListQueryRunEventsQuery query) {
        Objects.requireNonNull(query, "查询事件读取请求不能为空");
        requirePermissions(query);
        var queryRunId = QueryRunId.parse(query.queryRunId());
        var run = repository
                .find(query.context().tenantId(), queryRunId)
                .orElseThrow(QueryRunNotFoundException::new);
        var events = repository.findEvents(
                query.context().tenantId(),
                queryRunId,
                query.afterSequence(),
                query.limit());
        var views = events.stream()
                .map(QueryRunEventService::toView)
                .toList();
        var resumeAfterSequence = events.isEmpty()
                ? query.afterSequence()
                : events.getLast().sequence();
        return new QueryRunEventPage(
                views,
                resumeAfterSequence,
                run.status() != QueryRunStatus.RUNNING);
    }

    private static QueryRunEventView toView(QueryRunEvent event) {
        return new QueryRunEventView(
                event.sequence(),
                event.type().name(),
                event.status().name(),
                event.answer(),
                event.refusalReason() == null
                        ? null
                        : event.refusalReason().name(),
                event.citations().stream()
                        .map(QueryRunEventService::toView)
                        .toList(),
                event.usage(),
                event.occurredAt(),
                event.terminal());
    }

    private static QueryCitationView toView(QueryCitation citation) {
        return new QueryCitationView(
                citation.sequence(),
                Long.toString(citation.dataSourceId()),
                citation.contentVersion(),
                citation.chunkId(),
                citation.ordinal(),
                citation.quote(),
                citation.score());
    }

    private static void requirePermissions(ListQueryRunEventsQuery query) {
        if (!query.context().hasPermission(QUERY)
                || !query.context().hasPermission(KNOWLEDGE_READ)) {
            throw new CandidateSearchPermissionDeniedException();
        }
    }
}
