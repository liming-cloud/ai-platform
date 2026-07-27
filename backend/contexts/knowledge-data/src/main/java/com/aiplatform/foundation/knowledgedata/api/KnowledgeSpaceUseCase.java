package com.aiplatform.foundation.knowledgedata.api;

import com.aiplatform.foundation.knowledgedata.api.model.knowledge.CreateKnowledgeSpaceCommand;
import com.aiplatform.foundation.knowledgedata.api.model.knowledge.CreateKnowledgeSpaceResult;
import com.aiplatform.foundation.knowledgedata.api.model.knowledge.KnowledgeSpacePage;
import com.aiplatform.foundation.knowledgedata.api.model.knowledge.ListKnowledgeSpacesQuery;

/**
 * 知识空间创建和查询的公开应用契约。
 */
public interface KnowledgeSpaceUseCase {

    CreateKnowledgeSpaceResult create(CreateKnowledgeSpaceCommand command);

    KnowledgeSpacePage list(ListKnowledgeSpacesQuery query);
}
