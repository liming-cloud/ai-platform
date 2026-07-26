package com.aiplatform.foundation.assistantdelivery.api;

import com.aiplatform.foundation.assistantdelivery.api.model.ListQueryAssistantsQuery;
import com.aiplatform.foundation.assistantdelivery.api.model.QueryAssistantPage;

/**
 * 向查询渠道提供当前租户可用的已发布助手目录。
 */
public interface QueryAssistantCatalogUseCase {

    QueryAssistantPage list(ListQueryAssistantsQuery query);
}
