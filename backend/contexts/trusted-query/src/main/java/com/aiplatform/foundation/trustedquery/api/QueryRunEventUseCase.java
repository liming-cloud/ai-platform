package com.aiplatform.foundation.trustedquery.api;

import com.aiplatform.foundation.trustedquery.api.model.ListQueryRunEventsQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunEventPage;

/**
 * 按单调序号读取可重放查询事件的公共用例。
 */
public interface QueryRunEventUseCase {

    QueryRunEventPage list(ListQueryRunEventsQuery query);
}
