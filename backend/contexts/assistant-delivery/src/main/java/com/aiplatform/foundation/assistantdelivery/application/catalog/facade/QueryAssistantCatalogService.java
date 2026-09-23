package com.aiplatform.foundation.assistantdelivery.application.catalog.facade;

import com.aiplatform.foundation.shared.contract.paging.PageResult;

/** 在可信租户与查询权限边界内投影最新已发布助手版本。 */
public interface QueryAssistantCatalogService {

    /** 在请求者的租户和查询权限范围内返回已发布助手分页。 */
    PageResult<QueryAssistantView> list(ListQueryAssistantsQuery query);
}
