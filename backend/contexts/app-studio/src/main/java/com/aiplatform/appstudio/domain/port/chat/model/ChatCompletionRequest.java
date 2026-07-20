package com.aiplatform.appstudio.domain.port.chat.model;

import com.aiplatform.appstudio.domain.Citation;
import com.aiplatform.appstudio.domain.port.AppStudioPortContracts;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.kernel.ValueObject;
import java.util.List;

/**
 * 聊天生成请求值对象，运行时描述必须来自模型使用策略决策。
 */
public record ChatCompletionRequest(
        TenantId tenantId,
        String applicationId,
        String actorId,
        String traceId,
        String idempotencyKey,
        String applicationInstruction,
        String userQuestion,
        List<Citation> citations,
        AppModelRuntimeDescriptor runtimeDescriptor
) implements ValueObject {
    public ChatCompletionRequest(
            TenantId tenantId,
            String applicationId,
            String actorId,
            String applicationInstruction,
            String userQuestion,
            List<Citation> citations,
            AppModelRuntimeDescriptor runtimeDescriptor
    ) {
        this(tenantId, applicationId, actorId, null, legacyIdempotencyKey(applicationId, actorId, null),
                applicationInstruction, userQuestion, citations, runtimeDescriptor);
    }

    public ChatCompletionRequest(
            TenantId tenantId,
            String applicationId,
            String actorId,
            String traceId,
            String applicationInstruction,
            String userQuestion,
            List<Citation> citations,
            AppModelRuntimeDescriptor runtimeDescriptor
    ) {
        this(tenantId, applicationId, actorId, traceId, legacyIdempotencyKey(applicationId, actorId, traceId),
                applicationInstruction, userQuestion, citations, runtimeDescriptor);
    }

    public ChatCompletionRequest {
        tenantId = AppStudioPortContracts.requireValue(tenantId, "租户 ID");
        applicationId = AppStudioPortContracts.requireText(applicationId, "应用 ID");
        actorId = AppStudioPortContracts.requireText(actorId, "操作者 ID");
        traceId = normalizeOptionalText(traceId);
        idempotencyKey = AppStudioPortContracts.requireText(idempotencyKey, "调用幂等键");
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("调用幂等键长度不能超过 128");
        }
        applicationInstruction = AppStudioPortContracts.requireText(applicationInstruction, "应用系统指令");
        userQuestion = AppStudioPortContracts.requireText(userQuestion, "用户问题");
        citations = AppStudioPortContracts.copyRequiredList(citations, "引用列表");
        if (citations.isEmpty()) {
            throw new IllegalArgumentException("检索引用不能为空，证据不足时禁止调用模型");
        }
        runtimeDescriptor = AppStudioPortContracts.requireValue(runtimeDescriptor, "模型运行时描述");
    }

    /**
     * 只用于不可逆审计摘要与 token 粗估；模型消息必须由 adapter 按角色边界组装。
     */
    public String promptFingerprintMaterial() {
        StringBuilder material = new StringBuilder(applicationInstruction)
                .append('\n')
                .append(userQuestion);
        for (Citation citation : citations) {
            material.append('\n')
                    .append(citation.knowledgeBaseId()).append('/')
                    .append(citation.documentId()).append('/')
                    .append(citation.chunkId()).append(':')
                    .append(citation.fragment());
        }
        return material.toString();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String legacyIdempotencyKey(String applicationId, String actorId, String traceId) {
        String normalizedTraceId = normalizeOptionalText(traceId);
        return normalizedTraceId == null
                ? "legacy-" + AppStudioPortContracts.requireText(applicationId, "应用 ID")
                        + "-" + AppStudioPortContracts.requireText(actorId, "操作者 ID")
                : normalizedTraceId;
    }
}
