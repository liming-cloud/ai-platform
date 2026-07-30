package com.aiplatform.foundation.knowledgedata.application.datasource.command.publication;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/** 显式发布 OBJECT_STORAGE 来源同步产物的可信请求。 */
public record PublishObjectStorageSyncArtifactCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String dataSourceId,
        String artifactId,
        long expectedVersion) {

    public PublishObjectStorageSyncArtifactCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank()
                || dataSourceId == null || dataSourceId.isBlank()
                || artifactId == null || artifactId.isBlank()
                || expectedVersion < 0) {
            throw new IllegalArgumentException("对象存储同步产物发布请求不完整");
        }
    }
}
