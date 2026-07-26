package com.aiplatform.foundation.trustedquery.api;

import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunCommand;
import com.aiplatform.foundation.trustedquery.api.model.CreateQueryRunResult;
import com.aiplatform.foundation.trustedquery.api.model.GetQueryRunQuery;
import com.aiplatform.foundation.trustedquery.api.model.QueryRunView;

/**
 * 创建和读取可信查询运行的公共用例。
 */
public interface QueryRunUseCase {

    CreateQueryRunResult create(CreateQueryRunCommand command);

    QueryRunView get(GetQueryRunQuery query);
}
