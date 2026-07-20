package com.aiplatform.appstudio.domain;

import com.aiplatform.shared.kernel.AggregateRoot;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * AI 应用聚合根，绑定模型、知识库、工具和发布范围。
 */
public record AiApplication(
        TenantId tenantId,
        String applicationId,
        AiApplicationType type,
        String name,
        String description,
        String modelConfigId,
        List<String> knowledgeBaseIds,
        List<String> toolIds,
        Set<String> visiblePrincipalIds,
        String systemPrompt,
        ApplicationPublicationStatus publicationStatus,
        Instant createdAt,
        Instant updatedAt
) implements AggregateRoot<String> {
    public AiApplication {
        tenantId = requireValue(tenantId, "租户 ID");
        applicationId = requireText(applicationId, "应用 ID");
        type = requireValue(type, "应用类型");
        name = requireText(name, "应用名称");
        description = requireText(description, "应用描述");
        modelConfigId = requireText(modelConfigId, "模型配置 ID");
        knowledgeBaseIds = normalizeList(knowledgeBaseIds, "知识库 ID");
        toolIds = normalizeList(toolIds, "工具 ID");
        visiblePrincipalIds = normalizeSet(visiblePrincipalIds, "可见主体 ID");
        systemPrompt = requireText(systemPrompt, "系统提示词");
        publicationStatus = requireValue(publicationStatus, "发布状态");
        createdAt = requireValue(createdAt, "创建时间");
        updatedAt = requireValue(updatedAt, "更新时间");
    }

    public boolean canChat(String principalId) {
        return publicationStatus == ApplicationPublicationStatus.PUBLISHED
                && visiblePrincipalIds.contains(principalId);
    }

    @Override
    public String id() {
        return applicationId;
    }

    private static List<String> normalizeList(List<String> values, String fieldName) {
        requireValue(values, fieldName + "列表");
        return values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .toList();
    }

    private static Set<String> normalizeSet(Set<String> values, String fieldName) {
        requireValue(values, fieldName + "集合");
        return values.stream()
                .map(value -> requireText(value, fieldName))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
