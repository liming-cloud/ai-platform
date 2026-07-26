package com.aiplatform.foundation.controlplane.web.knowledge;

import java.util.UUID;

/**
 * 创建文本数据源的公共请求。
 */
public record TextDataSourceRequest(
        UUID knowledgeSpaceId,
        String name) {
}
