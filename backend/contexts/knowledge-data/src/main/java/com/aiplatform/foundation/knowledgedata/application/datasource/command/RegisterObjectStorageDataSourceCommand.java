package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/** 知识管理员提交平台已签发对象范围选择的可信登记请求。 */
public record RegisterObjectStorageDataSourceCommand(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String name,
        String rangeId,
        long selectionRevision) {

    public RegisterObjectStorageDataSourceCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank()
                || name == null || name.isBlank()
                || rangeId == null || rangeId.isBlank()
                || selectionRevision < 1) {
            throw new IllegalArgumentException("对象存储来源登记请求不完整");
        }
    }
}
