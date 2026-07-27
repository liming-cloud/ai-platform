package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.assistantdelivery.api.QueryAssistantCatalogUseCase;
import com.aiplatform.foundation.assistantdelivery.api.model.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.api.model.QueryAssistantPage;
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

    private final QueryAssistantCatalogUseCase assistants;

    public QueryAssistantController(QueryAssistantCatalogUseCase assistants) {
        this.assistants = Objects.requireNonNull(assistants, "查询助手目录不能为空");
    }

    @GetMapping
    public QueryAssistantPage list(
            TrustedRequestContext context,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return assistants.list(new ListQueryAssistantsQuery(context, cursor, limit));
    }
}
