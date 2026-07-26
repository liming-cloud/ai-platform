package com.aiplatform.foundation.controlplane.web.knowledge;

import java.util.UUID;

/**
 * 发布文本数据源的公共请求。
 */
public record PublishTextDataSourceRequest(
        UUID knowledgeSpaceId,
        Long expectedVersion) {
}
