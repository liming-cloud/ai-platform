package com.aiplatform.knowledge.application.command;

import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelCapability;
import com.aiplatform.knowledge.domain.port.modelpolicy.model.KnowledgeModelUseCase;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import com.aiplatform.shared.security.model.PermissionCode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 切换知识库挂载向量模型命令。切换必须先探活新模型，成功后再触发全量重索引。
 */
public record SwitchKnowledgeBaseEmbeddingModelCommand(
        TenantId tenantId,
        String knowledgeBaseId,
        String operatorId,
        String actorType,
        Set<String> roleCodes,
        Set<PermissionCode> permissionSnapshot,
        String embeddingModelConfigId
) implements ValueObject {
    public SwitchKnowledgeBaseEmbeddingModelCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库 ID");
        operatorId = requireText(operatorId, "操作人 ID");
        actorType = requireText(actorType, "操作者类型");
        roleCodes = normalizeRoleCodes(roleCodes);
        permissionSnapshot = copyPermissionSnapshot(permissionSnapshot);
        embeddingModelConfigId = requireText(embeddingModelConfigId, "目标向量模型配置 ID");
    }

    public KnowledgeModelUseCase useCase() {
        return KnowledgeModelUseCase.RAG_INDEXING;
    }

    public KnowledgeModelCapability requiredCapability() {
        return KnowledgeModelCapability.EMBEDDING;
    }

    private static Set<String> normalizeRoleCodes(Set<String> values) {
        Objects.requireNonNull(values, "角色快照不能为空");
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireText(value, "角色编码"));
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("角色快照不能为空");
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<PermissionCode> copyPermissionSnapshot(Set<PermissionCode> values) {
        Objects.requireNonNull(values, "权限快照不能为空");
        Set<PermissionCode> copied = new LinkedHashSet<>();
        for (PermissionCode value : values) {
            if (value == null) {
                throw new IllegalArgumentException("权限快照不能为空");
            }
            copied.add(value);
        }
        return Collections.unmodifiableSet(copied);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
