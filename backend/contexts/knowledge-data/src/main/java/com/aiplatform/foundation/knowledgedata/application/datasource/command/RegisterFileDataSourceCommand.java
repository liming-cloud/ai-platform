package com.aiplatform.foundation.knowledgedata.application.datasource.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.io.InputStream;
import java.util.Objects;

/** 由知识管理员提交文件正文的可信登记请求。 */
public record RegisterFileDataSourceCommand(
        TrustedRequestContext context, String knowledgeSpaceId, String name, InputStream content) {
    public RegisterFileDataSourceCommand {
        context = Objects.requireNonNull(context, "可信请求上下文不能为空");
        content = Objects.requireNonNull(content, "文件内容不能为空");
        if (knowledgeSpaceId == null || knowledgeSpaceId.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("文件来源登记请求不完整");
        }
    }
}
