package com.aiplatform.knowledge.domain.retrieval;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 检索访问过滤器，在 Prompt 组装前执行租户、知识库、有效期和 ACL 二次校验。
 */
public record RetrievalAccessFilter(
        String tenantId,
        List<String> knowledgeBaseIds,
        List<String> aclTokens,
        Instant now
) {
    public RetrievalAccessFilter {
        tenantId = requireText(tenantId, "租户");
        knowledgeBaseIds = normalizeRequired(knowledgeBaseIds, "知识库");
        aclTokens = normalizeRequired(aclTokens, "权限");
        Objects.requireNonNull(now, "校验时间不能为空");
    }

    public boolean allows(RetrievalCandidate candidate) {
        Objects.requireNonNull(candidate, "召回候选不能为空");
        if (!tenantId.equals(candidate.tenantId())) {
            return false;
        }
        if (!knowledgeBaseIds.contains(candidate.knowledgeBaseId())) {
            return false;
        }
        if (!candidate.metadata().visibleAt(now)) {
            return false;
        }
        Set<String> principalAclTokens = new HashSet<>(aclTokens);
        return visibilityAllowed(candidate, principalAclTokens)
                && classificationAllowed(candidate, principalAclTokens)
                && candidate.metadata().aclTokens().stream().anyMatch(principalAclTokens::contains);
    }

    private static boolean visibilityAllowed(RetrievalCandidate candidate, Set<String> principalAclTokens) {
        String visibility = candidate.metadata().visibility().trim().toLowerCase(Locale.ROOT);
        return switch (visibility) {
            case "public", "tenant" -> principalAclTokens.contains("tenant:" + candidate.tenantId());
            case "private" -> candidate.metadata().aclTokens().stream()
                    .filter(token -> token.startsWith("owner:") || token.startsWith("user:"))
                    .anyMatch(principalAclTokens::contains);
            case "department", "project", "specified_members", "specified_roles" -> true;
            default -> false;
        };
    }

    private static boolean classificationAllowed(RetrievalCandidate candidate, Set<String> principalAclTokens) {
        String classification = candidate.metadata().classification().trim().toLowerCase(Locale.ROOT);
        return switch (classification) {
            case "public", "internal" -> true;
            case "confidential", "restricted" -> principalAclTokens.contains("classification:" + classification)
                    || principalAclTokens.contains("permission:knowledge-base:classification-" + classification + ":read");
            default -> false;
        };
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
