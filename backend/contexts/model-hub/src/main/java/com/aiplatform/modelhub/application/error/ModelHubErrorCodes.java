package com.aiplatform.modelhub.application.error;

import com.aiplatform.shared.kernel.ErrorCode;

/**
 * 模型中心应用层错误码，统一承载 API、日志和审计中的失败原因。
 */
public final class ModelHubErrorCodes {
    public static final ErrorCode INVALID_PROVIDER_CONFIG =
            ErrorCode.of("AIP-MDL-INVALIDCONFIG", "模型供应商配置不合法");
    public static final ErrorCode INVALID_MODEL_CONFIG =
            ErrorCode.of("AIP-MDL-MODELCONFIG", "模型配置不合法");
    public static final ErrorCode PROVIDER_NOT_FOUND =
            ErrorCode.of("AIP-MDL-PROVIDERNOTFOUND", "模型供应商不存在");
    public static final ErrorCode MODEL_CONFIG_NOT_FOUND =
            ErrorCode.of("AIP-MDL-CONFIGNOTFOUND", "模型配置不存在");
    public static final ErrorCode MODEL_CONFIG_DUPLICATED =
            ErrorCode.of("AIP-MDL-CONFIGDUPLICATED", "模型配置已存在");
    public static final ErrorCode VERSION_CONFLICT =
            ErrorCode.of("AIP-MDL-VERSIONCONFLICT", "资源版本冲突，请刷新后重试");
    public static final ErrorCode CREATION_IDEMPOTENCY_CONFLICT =
            ErrorCode.of("AIP-MDL-CREATIONIDEMPOTENCYCONFLICT", "创建请求与既有幂等请求不一致");

    private ModelHubErrorCodes() {
    }
}
