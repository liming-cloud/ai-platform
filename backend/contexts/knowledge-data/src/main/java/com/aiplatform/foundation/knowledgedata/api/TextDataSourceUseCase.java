package com.aiplatform.foundation.knowledgedata.api;

import com.aiplatform.foundation.knowledgedata.api.model.datasource.CreateTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.datasource.CreateTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.api.model.datasource.ListTextDataSourcesQuery;
import com.aiplatform.foundation.knowledgedata.api.model.datasource.PublishTextDataSourceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.datasource.PublishTextDataSourceResult;
import com.aiplatform.foundation.knowledgedata.api.model.datasource.TextDataSourcePage;
import com.aiplatform.foundation.knowledgedata.api.model.datasource.UploadTextContentCommand;
import com.aiplatform.foundation.knowledgedata.api.model.datasource.UploadTextContentResult;

/**
 * 文本数据源登记、查询和发布的公开应用契约。
 */
public interface TextDataSourceUseCase {

    CreateTextDataSourceResult create(CreateTextDataSourceCommand command);

    TextDataSourcePage list(ListTextDataSourcesQuery query);

    UploadTextContentResult upload(UploadTextContentCommand command);

    PublishTextDataSourceResult publish(PublishTextDataSourceCommand command);
}
