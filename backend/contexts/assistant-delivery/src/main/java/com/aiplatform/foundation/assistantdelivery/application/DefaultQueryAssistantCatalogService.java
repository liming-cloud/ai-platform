package com.aiplatform.foundation.assistantdelivery.application;

import com.aiplatform.foundation.assistantdelivery.api.QueryAssistantCatalogUseCase;
import com.aiplatform.foundation.assistantdelivery.api.model.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.api.model.QueryAssistantPage;
import com.aiplatform.foundation.assistantdelivery.api.model.QueryAssistantView;
import com.aiplatform.foundation.assistantdelivery.application.port.out.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.application.port.out.PublishedAssistantSummary;
import com.aiplatform.foundation.security.identity.PermissionCode;
import java.util.Objects;

/**
 * 在可信租户与查询权限边界内投影最新已发布助手版本。
 */
public final class DefaultQueryAssistantCatalogService
        implements QueryAssistantCatalogUseCase {

    private static final PermissionCode QUERY =
            new PermissionCode("assistant:query");
    private static final PermissionCode KNOWLEDGE_READ =
            new PermissionCode("knowledge:read");

    private final AssistantRepository assistants;

    public DefaultQueryAssistantCatalogService(AssistantRepository assistants) {
        this.assistants = Objects.requireNonNull(assistants, "助手仓储不能为空");
    }

    @Override
    public QueryAssistantPage list(ListQueryAssistantsQuery query) {
        Objects.requireNonNull(query, "查询助手目录请求不能为空");
        if (!query.context().hasPermission(QUERY)
                || !query.context().hasPermission(KNOWLEDGE_READ)) {
            throw new AssistantPermissionDeniedException();
        }
        var slice = assistants.findPublishedPage(
                query.context().tenantId(),
                query.cursor(),
                query.limit());
        return new QueryAssistantPage(
                slice.items().stream()
                        .map(DefaultQueryAssistantCatalogService::toView)
                        .toList(),
                slice.nextCursor());
    }

    private static QueryAssistantView toView(PublishedAssistantSummary summary) {
        return new QueryAssistantView(
                Long.toString(summary.assistantId()),
                summary.displayName(),
                Long.toString(summary.assistantVersionId()),
                summary.versionNumber(),
                summary.publishedAt());
    }
}
