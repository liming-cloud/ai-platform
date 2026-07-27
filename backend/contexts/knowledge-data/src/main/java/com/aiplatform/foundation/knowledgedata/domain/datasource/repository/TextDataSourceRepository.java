package com.aiplatform.foundation.knowledgedata.domain.datasource.repository;

import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.knowledge.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextDataSource;
import java.util.Optional;

/**
 * 文本数据源事务边界。
 */
public interface TextDataSourceRepository {

    TextDataSourceMutationOutcome create(
            TextDataSourceCreation creation,
            int tenantLimit);

    TextDataSourceMutationOutcome attach(TextContentAttachment attachment);

    TextDataSourceMutationOutcome publish(TextDataSourcePublication publication);

    TextDataSource findById(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            DataSourceId dataSourceId);

    Optional<TextDataSourceMutationOutcome> findAttachmentReplay(
            TenantId tenantId,
            String idempotencyKey,
            String requestFingerprint);

    Optional<TextDataSourceMutationOutcome> findPublicationReplay(
            TenantId tenantId,
            String idempotencyKey,
            String requestFingerprint);

    TextDataSourcePage findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            long pageNumber,
            long pageSize);
}
