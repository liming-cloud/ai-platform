package com.aiplatform.modelhub.presentation.converter;

import com.aiplatform.modelhub.application.result.ModelConfigDetailResult;
import com.aiplatform.modelhub.application.result.ModelProviderDetailResult;
import com.aiplatform.modelhub.presentation.dto.ModelConfigDetailResponse;
import com.aiplatform.modelhub.presentation.dto.ModelProviderDetailResponse;
import org.springframework.stereotype.Component;

/**
 * 模型中心展示层转换器，隔离应用结果和 HTTP 响应 DTO。
 */
@Component
public class ModelHubPresentationConverter {
    public ModelProviderDetailResponse toResponse(ModelProviderDetailResult result) {
        return new ModelProviderDetailResponse(
                result.providerId(),
                result.providerCode(),
                result.providerName(),
                result.endpoint(),
                result.status(),
                result.approvalRequestId(),
                result.version()
        );
    }

    public ModelConfigDetailResponse toResponse(ModelConfigDetailResult result) {
        return new ModelConfigDetailResponse(
                result.providerId(),
                result.modelConfigId(),
                result.modelCode(),
                result.modelName(),
                result.modelType(),
                result.contextWindow(),
                result.embeddingDimension(),
                result.inputPricePer1k(),
                result.outputPricePer1k(),
                result.providerKind(),
                result.temperature(),
                result.topP(),
                result.maxTokens(),
                result.status(),
                result.approvalRequestId(),
                result.connectivityStatus(),
                result.connectivityMessage(),
                result.version()
        );
    }
}
