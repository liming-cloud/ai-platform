package com.aiplatform.infra.springai.model.request;

import com.aiplatform.shared.kernel.ModelRuntimeContracts;
import java.util.Map;

/**
 * Spring AI 模型运行时请求，承载厂商、模型、鉴权、凭证材料和 options 配置。
 */
public record SpringAiModelRuntimeRequest(
        String providerCode,
        String providerKind,
        String endpoint,
        String authType,
        String modelCode,
        String modelType,
        Integer contextWindow,
        Integer embeddingDimension,
        Map<String, Object> options,
        String credentialMaterial
) {
    public SpringAiModelRuntimeRequest {
        providerCode = ModelRuntimeContracts.requireProviderCode(providerCode);
        providerKind = ModelRuntimeContracts.requireProviderKind(providerKind);
        endpoint = ModelRuntimeContracts.requireText(endpoint, "供应商地址");
        authType = ModelRuntimeContracts.requireText(authType, "鉴权方式");
        modelCode = ModelRuntimeContracts.requireModelCode(modelCode);
        modelType = ModelRuntimeContracts.requireText(modelType, "模型类型");
        options = ModelRuntimeContracts.copyRequiredMap(options, "模型运行时 options");
    }
}
