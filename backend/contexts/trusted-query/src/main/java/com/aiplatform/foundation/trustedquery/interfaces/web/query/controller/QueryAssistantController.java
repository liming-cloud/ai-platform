package com.aiplatform.foundation.trustedquery.interfaces.web.query.controller;

import com.aiplatform.foundation.assistantdelivery.application.catalog.service.QueryAssistantCatalogService;
import com.aiplatform.foundation.assistantdelivery.application.catalog.query.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.application.catalog.dto.QueryAssistantView;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最终用户读取当前租户已发布助手目录的在线入口。
 */
@RestController
@RequestMapping("/api/v1/query-assistants")
@ConditionalOnProperty(name = "foundation.query.enabled", havingValue = "true")
public class QueryAssistantController {

    private final QueryAssistantCatalogService assistants;

    public QueryAssistantController(QueryAssistantCatalogService assistants) {
        this.assistants = Objects.requireNonNull(assistants, "查询助手目录不能为空");
    }

    @GetMapping
    public PageResult<QueryAssistantView> list(
            TrustedRequestContext context,
            @RequestParam(defaultValue = "1") long pageNumber,
            @RequestParam(defaultValue = "20") long pageSize) {
        return assistants.list(new ListQueryAssistantsQuery(
                context,
                new PageQuery(pageNumber, pageSize)));
    }
}
