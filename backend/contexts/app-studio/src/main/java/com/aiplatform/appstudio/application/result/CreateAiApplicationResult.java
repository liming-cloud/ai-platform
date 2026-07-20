package com.aiplatform.appstudio.application.result;

import java.time.Instant;
import java.util.List;

/**
 * 创建 AI 应用结果，返回应用主键、模型绑定与可见范围。
 */
public record CreateAiApplicationResult(
        String applicationId,
        String applicationCode,
        String name,
        String modelConfigId,
        List<String> knowledgeBaseIds,
        String publicationStatus,
        Instant createdAt,
        ApplicationAccessMode accessMode,
        ApplicationCapabilitiesResult capabilities
) {
}
