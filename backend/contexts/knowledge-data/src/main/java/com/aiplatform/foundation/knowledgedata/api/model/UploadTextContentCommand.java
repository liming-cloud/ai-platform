package com.aiplatform.foundation.knowledgedata.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * 流式上传文本数据源内容的命令。
 */
public record UploadTextContentCommand(
        TrustedRequestContext context,
        UUID knowledgeSpaceId,
        UUID dataSourceId,
        long expectedVersion,
        String idempotencyKey,
        String mediaType,
        InputStream content) {

    public UploadTextContentCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(mediaType, "媒体类型不能为空");
        Objects.requireNonNull(content, "文本内容流不能为空");
    }
}
