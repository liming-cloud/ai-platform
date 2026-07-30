package com.aiplatform.foundation.knowledgedata.application.datasource.command.publication;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 显式发布 STRUCTURED_DATASET 来源同步产物的可信请求。
 *
 * <p>租户与权限只从可信上下文取得；调用方只能选择已存在的来源和同步产物，不能提交 CSV 正文、对象位置或质量结论。</p>
 */
public record PublishStructuredDatasetSyncArtifactCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String dataSourceId,
        String artifactId,
        long expectedVersion) {

    public PublishStructuredDatasetSyncArtifactCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank()
                || dataSourceId == null || dataSourceId.isBlank()
                || artifactId == null || artifactId.isBlank()
                || expectedVersion < 0) {
            throw new IllegalArgumentException("结构化数据集同步产物发布请求不完整");
        }
    }
}
