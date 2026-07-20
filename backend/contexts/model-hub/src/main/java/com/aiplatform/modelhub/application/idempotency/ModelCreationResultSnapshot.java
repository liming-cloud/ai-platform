package com.aiplatform.modelhub.application.idempotency;

import com.aiplatform.modelhub.application.result.CreateModelConfigResult;
import com.aiplatform.modelhub.application.result.CreateModelProviderResult;

/** Immutable, non-secret response snapshot returned for every replay of one create request. */
public record ModelCreationResultSnapshot(
        ModelCreationResourceType resourceType,
        String parentResourceId,
        String resourceId,
        String resourceCode,
        String modelType,
        String status,
        String approvalRequestId,
        String connectivityStatus
) {
    public ModelCreationResultSnapshot {
        if (resourceType == null) {
            throw new IllegalArgumentException("模型创建结果资源类型不能为空");
        }
        resourceId = requireText(resourceId, "模型创建结果资源 ID");
        status = requireText(status, "模型创建结果状态");
        if (resourceType == ModelCreationResourceType.MODEL_PROVIDER) {
            approvalRequestId = requireText(approvalRequestId, "供应商创建结果审批请求 ID");
            requireAbsent(parentResourceId, "供应商创建结果父资源 ID");
            requireAbsent(resourceCode, "供应商创建结果资源编码");
            requireAbsent(modelType, "供应商创建结果模型类型");
            requireAbsent(connectivityStatus, "供应商创建结果连通性状态");
        } else {
            parentResourceId = requireText(parentResourceId, "模型创建结果供应商 ID");
            resourceCode = requireText(resourceCode, "模型创建结果模型编码");
            modelType = requireText(modelType, "模型创建结果模型类型");
            connectivityStatus = requireText(connectivityStatus, "模型创建结果连通性状态");
            approvalRequestId = normalizeOptionalText(approvalRequestId, "模型创建结果审批请求 ID");
            if ("PENDING_APPROVAL".equals(status) && approvalRequestId == null) {
                throw new IllegalArgumentException("待审批模型创建结果必须携带审批请求 ID");
            }
        }
    }

    public static ModelCreationResultSnapshot from(CreateModelProviderResult result) {
        return new ModelCreationResultSnapshot(
                ModelCreationResourceType.MODEL_PROVIDER,
                null,
                result.providerId(),
                null,
                null,
                result.status(),
                result.approvalRequestId(),
                null
        );
    }

    public static ModelCreationResultSnapshot from(CreateModelConfigResult result) {
        return new ModelCreationResultSnapshot(
                ModelCreationResourceType.MODEL_CONFIG,
                result.providerId(),
                result.modelConfigId(),
                result.modelCode(),
                result.modelType(),
                result.status(),
                result.approvalRequestId(),
                result.connectivityStatus()
        );
    }

    public CreateModelProviderResult toProviderResult() {
        requireType(ModelCreationResourceType.MODEL_PROVIDER);
        return new CreateModelProviderResult(resourceId, status, approvalRequestId);
    }

    public CreateModelConfigResult toModelConfigResult() {
        requireType(ModelCreationResourceType.MODEL_CONFIG);
        return new CreateModelConfigResult(
                parentResourceId,
                resourceId,
                resourceCode,
                modelType,
                status,
                approvalRequestId,
                connectivityStatus
        );
    }

    private void requireType(ModelCreationResourceType expected) {
        if (resourceType != expected) {
            throw new IllegalStateException("模型创建结果快照类型不匹配");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static void requireAbsent(String value, String fieldName) {
        if (value != null) {
            throw new IllegalArgumentException(fieldName + "必须为空");
        }
    }

    private static String normalizeOptionalText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空白");
        }
        return value.trim();
    }
}
