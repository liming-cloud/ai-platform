package com.aiplatform.foundation.trustedquery.application.run;

import com.aiplatform.foundation.trustedquery.application.candidate.CandidateSearchPermissionDeniedException;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.trustedquery.api.QueryRunEventUseCase;
import com.aiplatform.foundation.trustedquery.api.model.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryCitationView;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunEventPage;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunEventView;
import com.aiplatform.foundation.trustedquery.application.port.out.QueryRunRepository;
import com.aiplatform.foundation.trustedquery.domain.QueryRunId;
import com.aiplatform.foundation.trustedquery.domain.QueryCitation;
import com.aiplatform.foundation.trustedquery.domain.QueryRunEvent;
import com.aiplatform.foundation.trustedquery.domain.QueryRunStatus;
import java.util.Objects;

/**
 * 在可信租户边界内按游标重放查询事件。
 */
public final class DefaultQueryRunEventService implements QueryRunEventUseCase {

    private static final PermissionCode QUERY = new PermissionCode("assistant:query");
    private static final PermissionCode KNOWLEDGE_READ =
            new PermissionCode("knowledge:read");

    private final QueryRunRepository repository;

    public DefaultQueryRunEventService(QueryRunRepository repository) {
        this.repository = Objects.requireNonNull(repository, "查询运行仓储不能为空");
    }

    @Override
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
                .map(DefaultQueryRunEventService::toView)
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
                        .map(DefaultQueryRunEventService::toView)
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
