package com.aiplatform.foundation.knowledgedata.api;

import com.aiplatform.foundation.knowledgedata.api.model.CreateTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.CreateTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.api.model.ListTextDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.api.model.PublishTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.PublishTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.api.model.TextDataSourcePage;
import com.aiplatform.foundation.knowledgedata.api.model.UploadTextContentCommand;
import com.aiplatform.foundation.knowledgedata.api.model.UploadTextContentResult;

/**
 * 文本数据源登记、查询和发布的公开应用契约。
 */
public interface TextDataSourceUseCase {

    CreateTextDataSourceResult create(CreateTextDataSourceCommand command);

    TextDataSourcePage list(ListTextDataSourcesQuery query);

    UploadTextContentResult upload(UploadTextContentCommand command);

    PublishTextDataSourceResult publish(PublishTextDataSourceCommand command);
}
