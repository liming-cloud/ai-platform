package com.aiplatform.foundation.modelruntime.application.platformsupply.query;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import java.util.Objects;

/** 读取平台供应商连接的标准分页查询。 */
public record ListPlatformProviderConnectionsQuery(
        PlatformRequestContext context,
        PageQuery page) {

    public ListPlatformProviderConnectionsQuery {
        Objects.requireNonNull(context, "平台请求上下文不能为空");
        Objects.requireNonNull(page, "分页参数不能为空");
    }
}
