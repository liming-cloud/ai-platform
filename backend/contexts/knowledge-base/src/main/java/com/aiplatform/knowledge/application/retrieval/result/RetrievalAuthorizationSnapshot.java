package com.aiplatform.knowledge.application.retrieval.result;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 检索授权快照，仅返回当前主体的角色、权限和知识库授权范围，不暴露其他主体 ACL。
 */
public record RetrievalAuthorizationSnapshot(
        List<String> roleCodes,
        List<String> permissionCodes,
        List<String> requestedKnowledgeBaseIds,
        List<String> authorizedKnowledgeBaseIds
) {
    public RetrievalAuthorizationSnapshot {
        roleCodes = normalize(roleCodes, "角色编码");
        permissionCodes = normalize(permissionCodes, "权限编码");
        requestedKnowledgeBaseIds = normalize(requestedKnowledgeBaseIds, "请求知识库 ID");
        authorizedKnowledgeBaseIds = normalize(authorizedKnowledgeBaseIds, "授权知识库 ID");
        if (!requestedKnowledgeBaseIds.containsAll(authorizedKnowledgeBaseIds)) {
            throw new IllegalArgumentException("授权知识库必须属于本次请求范围");
        }
    }

    public static RetrievalAuthorizationSnapshot empty() {
        return new RetrievalAuthorizationSnapshot(List.of(), List.of(), List.of(), List.of());
    }

    public static RetrievalAuthorizationSnapshot of(
            Collection<String> roleCodes,
            Collection<String> permissionCodes,
            Collection<String> requestedKnowledgeBaseIds,
            Collection<String> authorizedKnowledgeBaseIds
    ) {
        return new RetrievalAuthorizationSnapshot(
                List.copyOf(Objects.requireNonNull(roleCodes, "角色编码不能为空")),
                List.copyOf(Objects.requireNonNull(permissionCodes, "权限编码不能为空")),
                List.copyOf(Objects.requireNonNull(requestedKnowledgeBaseIds, "请求知识库 ID 不能为空")),
                List.copyOf(Objects.requireNonNull(authorizedKnowledgeBaseIds, "授权知识库 ID 不能为空"))
        );
    }

    private static List<String> normalize(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        return values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .sorted()
                .toList();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
