package com.aiplatform.foundation.knowledgedata.api;

import com.aiplatform.foundation.knowledgedata.api.model.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.CreateIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.api.model.IndexVersionPage;
import com.aiplatform.foundation.knowledgedata.api.model.ListIndexVersionsQuery;
import com.aiplatform.foundation.knowledgedata.api.model.PublishIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.PublishIndexVersionResult;

/**
 * 索引版本创建、查看和发布的公共用例。
 */
public interface IndexVersionUseCase {

    CreateIndexVersionResult create(CreateIndexVersionCommand command);

    IndexVersionPage list(ListIndexVersionsQuery query);

    PublishIndexVersionResult publish(PublishIndexVersionCommand command);
}
