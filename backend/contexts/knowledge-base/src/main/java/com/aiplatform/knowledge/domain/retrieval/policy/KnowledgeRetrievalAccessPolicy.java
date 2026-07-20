package com.aiplatform.knowledge.domain.retrieval.policy;

import com.aiplatform.knowledge.domain.KnowledgeBase;
import com.aiplatform.knowledge.domain.retrieval.RetrievalAccessFilter;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 知识检索权限策略，统一从已授权知识库和服务端主体上下文生成 ACL 快照。
 */
public class KnowledgeRetrievalAccessPolicy {
    public List<KnowledgeBase> authorizedBases(TenantId tenantId, String principalId, List<KnowledgeBase> requestedBases) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String normalizedPrincipalId = requirePrincipal(principalId);
        Objects.requireNonNull(requestedBases, "请求知识库不能为空");
        return requestedBases.stream()
                .filter(base -> tenantId.equals(base.tenantId()))
                .filter(base -> base.canBeRetrievedBy(normalizedPrincipalId))
                .toList();
    }

    public RetrievalAccessFilter accessFilter(
            TenantId tenantId,
            String principalId,
            List<KnowledgeBase> authorizedBases,
            Instant now
    ) {
        return accessFilter(tenantId, principalId, authorizedBases, Set.of(), Set.of(), now);
    }

    public RetrievalAccessFilter accessFilter(
            TenantId tenantId,
            String principalId,
            List<KnowledgeBase> authorizedBases,
            Set<String> roleCodes,
            Set<PermissionCode> permissionCodes,
            Instant now
    ) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String normalizedPrincipalId = requirePrincipal(principalId);
        Objects.requireNonNull(authorizedBases, "已授权知识库不能为空");
        Objects.requireNonNull(now, "校验时间不能为空");
        return new RetrievalAccessFilter(
                tenantId.value(),
                authorizedBases.stream().map(KnowledgeBase::knowledgeBaseId).toList(),
                principalAclTokens(tenantId, normalizedPrincipalId, authorizedBases, roleCodes, permissionCodes),
                now
        );
    }

    private static List<String> principalAclTokens(
            TenantId tenantId,
            String normalizedPrincipalId,
            List<KnowledgeBase> authorizedBases,
            Set<String> roleCodes,
            Set<PermissionCode> permissionCodes
    ) {
        ArrayList<String> tokens = new ArrayList<>();
        tokens.add("tenant:" + tenantId.value());
        tokens.add("user:" + normalizedPrincipalId);
        tokens.add("owner:" + normalizedPrincipalId);
        Objects.requireNonNull(roleCodes, "角色编码不能为空").stream()
                .map(roleCode -> "role:" + requireToken(roleCode, "角色编码"))
                .forEach(tokens::add);
        Objects.requireNonNull(permissionCodes, "权限编码不能为空").stream()
                .map(permission -> "permission:" + permission.value())
                .forEach(tokens::add);
        permissionCodes.stream()
                .map(PermissionCode::value)
                .filter(permission -> permission.startsWith("knowledge-base:classification-") && permission.endsWith(":read"))
                .map(permission -> "classification:" + permission.substring(
                        "knowledge-base:classification-".length(),
                        permission.length() - ":read".length()
                ))
                .forEach(tokens::add);
        authorizedBases.forEach(base -> tokens.addAll(base.retrievalAclTokensFor(normalizedPrincipalId)));
        return tokens.stream().distinct().toList();
    }

    private static String requireToken(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }

    private static String requirePrincipal(String principalId) {
        Objects.requireNonNull(principalId, "访问主体不能为空");
        if (principalId.isBlank()) {
            throw new IllegalArgumentException("访问主体不能为空白");
        }
        return principalId.trim();
    }
}
