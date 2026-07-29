package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 显式发布 FILE 来源同步产物的可信请求。
 *
 * <p>租户与权限仅从可信上下文取得；调用方只能选择已存在的来源与同步产物，不能提交对象位置、正文或质量结论。</p>
 */
public record PublishFileSyncArtifactCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String dataSourceId,
        String artifactId,
        long expectedVersion) {

    public PublishFileSyncArtifactCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank()
                || dataSourceId == null || dataSourceId.isBlank()
                || artifactId == null || artifactId.isBlank()
                || expectedVersion < 0) {
            throw new IllegalArgumentException("文件同步产物发布请求不完整");
        }
    }
}
