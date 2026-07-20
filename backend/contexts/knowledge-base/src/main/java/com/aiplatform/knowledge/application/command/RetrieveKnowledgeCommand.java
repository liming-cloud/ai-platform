package com.aiplatform.knowledge.application.command;

import com.aiplatform.knowledge.domain.retrieval.RetrievalProfile;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.util.Objects;

/**
 * 知识检索命令，权限标签必须由服务端上下文和权限服务生成，不能来自前端透传。
 */
public record RetrieveKnowledgeCommand(
        TenantId tenantId,
        String principalId,
        String principalType,
        List<String> knowledgeBaseIds,
        String query,
        RetrievalProfile profile,
        String answerModel
) {
    public RetrieveKnowledgeCommand {
        Objects.requireNonNull(tenantId, "租户上下文不能为空");
        principalId = requireText(principalId, "访问主体");
        principalType = requireText(principalType, "访问主体类型");
        knowledgeBaseIds = normalizeRequired(knowledgeBaseIds, "知识库范围");
        query = requireText(query, "检索问题");
        Objects.requireNonNull(profile, "检索画像不能为空");
        answerModel = normalizeOptionalText(answerModel);
    }

    private static List<String> normalizeRequired(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        List<String> normalized = values.stream()
                .map(value -> requireText(value, fieldName))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
