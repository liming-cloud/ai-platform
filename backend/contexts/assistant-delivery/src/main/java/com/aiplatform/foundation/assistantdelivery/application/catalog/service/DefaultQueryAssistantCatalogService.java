package com.aiplatform.foundation.assistantdelivery.application.catalog.service;

import com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.application.catalog.facade.QueryAssistantView;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.AssistantRepository;
import com.aiplatform.foundation.assistantdelivery.domain.assistant.repository.PublishedAssistantSummary;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import java.util.Objects;

/** 在拥有者上下文内读取助手领域仓储并转换为公开目录视图。 */
public final class DefaultQueryAssistantCatalogService implements QueryAssistantCatalogService {

    private static final PermissionCode QUERY = new PermissionCode("assistant:query");
    private static final PermissionCode KNOWLEDGE_READ = new PermissionCode("knowledge:read");

    private final AssistantRepository assistants;

    /** 仓储只注入拥有者内部实现，不进入公开 facade 构造签名。 */
    public DefaultQueryAssistantCatalogService(AssistantRepository assistants) {
        this.assistants = Objects.requireNonNull(assistants, "助手仓储不能为空");
    }

    @Override
    public PageResult<QueryAssistantView> list(ListQueryAssistantsQuery query) {
        Objects.requireNonNull(query, "查询助手目录请求不能为空");
        if (!query.context().hasPermission(QUERY)
                || !query.context().hasPermission(KNOWLEDGE_READ)) {
            throw new AssistantPermissionDeniedException();
        }
        var page = assistants.findPublishedPage(
                query.context().tenantId(),
                query.page().pageNumber(),
                query.page().pageSize());
        return PageResult.of(
                page.items().stream().map(DefaultQueryAssistantCatalogService::toView).toList(),
                query.page(), page.totalElements());
    }

    private static QueryAssistantView toView(PublishedAssistantSummary summary) {
        return new QueryAssistantView(
                Long.toString(summary.assistantId()), summary.displayName(),
                Long.toString(summary.assistantVersionId()), summary.versionNumber(),
                summary.publishedAt());
    }
}
