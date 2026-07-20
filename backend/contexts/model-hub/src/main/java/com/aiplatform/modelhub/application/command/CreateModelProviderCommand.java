package com.aiplatform.modelhub.application.command;

import com.aiplatform.modelhub.domain.ModelProviderAuthTypes;
import com.aiplatform.modelhub.application.idempotency.ModelCreationRequestId;
import com.aiplatform.shared.kernel.ApprovalEvidenceSecretPolicy;
import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 创建模型供应商命令，入口层必须先解析租户、操作者和凭证材料输入。
 */
public record CreateModelProviderCommand(
        TenantId tenantId,
        String operatorId,
        String requestId,
        String providerType,
        String providerName,
        String apiEndpoint,
        String authType,
        String credentialMaterialInput
) {
    public CreateModelProviderCommand {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        operatorId = requireText(operatorId, "操作人 ID");
        requestId = ModelCreationRequestId.require(requestId);
        providerType = requireText(providerType, "模型供应商类型");
        providerName = requireText(providerName, "模型供应商名称");
        apiEndpoint = requireText(apiEndpoint, "模型供应商访问地址");
        authType = ModelProviderAuthTypes.normalize(authType);
        if (ModelProviderAuthTypes.requiresCredentialMaterial(authType)) {
            requireCredentialMaterial(credentialMaterialInput, "模型供应商凭证材料");
        } else if (credentialMaterialInput != null && !credentialMaterialInput.isBlank()) {
            throw new IllegalArgumentException("no_auth 模型供应商不允许携带凭证材料");
        } else {
            credentialMaterialInput = null;
        }
        ApprovalEvidenceSecretPolicy.requireSecretFree(
                providerType,
                "模型供应商类型",
                credentialMaterialInput
        );
        ApprovalEvidenceSecretPolicy.requireSecretFree(
                providerName,
                "模型供应商名称",
                credentialMaterialInput
        );
        ApprovalEvidenceSecretPolicy.requireSecretFree(
                apiEndpoint,
                "模型供应商访问地址",
                credentialMaterialInput
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireCredentialMaterial(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 16_384) {
            throw new IllegalArgumentException(fieldName + "长度超限");
        }
    }
}
