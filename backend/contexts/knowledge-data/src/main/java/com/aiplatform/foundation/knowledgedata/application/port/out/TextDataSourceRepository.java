package com.aiplatform.foundation.knowledgedata.application.port.out;

/**
 * 文本数据源事务边界。
 */
public interface TextDataSourceRepository {

    TextDataSourceMutationOutcome create(
            TextDataSourceCreation creation,
            int tenantLimit);

    TextDataSourceMutationOutcome attach(TextContentAttachment attachment);

    TextDataSourceMutationOutcome publish(TextDataSourcePublication publication);
}
