package com.aiplatform.foundation.knowledgedata.application.port.out;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.knowledgedata.domain.model.KnowledgeSpaceId;
import com.aiplatform.foundation.knowledgedata.domain.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.model.TextDataSource;
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

    TextDataSourceSlice findPage(
            TenantId tenantId,
            KnowledgeSpaceId knowledgeSpaceId,
            String cursor,
            int limit);
}
