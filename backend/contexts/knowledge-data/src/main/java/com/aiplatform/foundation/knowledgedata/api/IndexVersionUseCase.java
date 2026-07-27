package com.aiplatform.foundation.knowledgedata.api;

import com.aiplatform.foundation.knowledgedata.api.model.index.CreateIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.index.CreateIndexVersionResult;
import com.aiplatform.foundation.knowledgedata.api.model.index.IndexVersionPage;
import com.aiplatform.foundation.knowledgedata.api.model.index.ListIndexVersionsQuery;
import com.aiplatform.foundation.knowledgedata.api.model.index.PublishIndexVersionCommand;
import com.aiplatform.foundation.knowledgedata.api.model.index.PublishIndexVersionResult;

/**
 * 索引版本创建、查看和发布的公共用例。
 */
public interface IndexVersionUseCase {

    CreateIndexVersionResult create(CreateIndexVersionCommand command);

    IndexVersionPage list(ListIndexVersionsQuery query);

    PublishIndexVersionResult publish(PublishIndexVersionCommand command);
}
