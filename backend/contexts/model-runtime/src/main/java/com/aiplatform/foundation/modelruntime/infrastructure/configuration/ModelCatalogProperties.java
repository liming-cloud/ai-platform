package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型目录的租户资源上限。
 */
@ConfigurationProperties("foundation.model-catalog")
public class ModelCatalogProperties {
    private java.util.List<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationPrice> platformVerificationPrices = java.util.List.of();

    /** 仅启动时装配已核实的非秘密计价材料；缺省不提供报价，正式验证零外呼失败关闭。 */
    public java.util.List<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationPrice> getPlatformVerificationPrices() {
        return platformVerificationPrices;
    }
    public void setPlatformVerificationPrices(java.util.List<com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationPrice> prices) {
        platformVerificationPrices = java.util.List.copyOf(prices);
    }

    private boolean platformDeploymentWritesAvailable = true;

    /** 实例启动时固定的 S2 写入可用性；不影响读取和安全停用。 */
    public boolean isPlatformDeploymentWritesAvailable() { return platformDeploymentWritesAvailable; }
    public void setPlatformDeploymentWritesAvailable(boolean value) { platformDeploymentWritesAvailable = value; }

    private int providerConnectionLimit = 3;
    private int modelDeploymentLimit = 6;
    private Duration capabilityVerificationValidity = Duration.ofHours(24);

    public int getProviderConnectionLimit() {
        return providerConnectionLimit;
    }

    public void setProviderConnectionLimit(int providerConnectionLimit) {
        if (providerConnectionLimit < 1) {
            throw new IllegalArgumentException("供应商连接上限必须大于零");
        }
        this.providerConnectionLimit = providerConnectionLimit;
    }

    public int getModelDeploymentLimit() {
        return modelDeploymentLimit;
    }

    public void setModelDeploymentLimit(int modelDeploymentLimit) {
        if (modelDeploymentLimit < 1) {
            throw new IllegalArgumentException("模型部署上限必须大于零");
        }
        this.modelDeploymentLimit = modelDeploymentLimit;
    }

    public Duration getCapabilityVerificationValidity() {
        return capabilityVerificationValidity;
    }

    public void setCapabilityVerificationValidity(Duration capabilityVerificationValidity) {
        if (capabilityVerificationValidity == null
                || capabilityVerificationValidity.isZero()
                || capabilityVerificationValidity.isNegative()) {
            throw new IllegalArgumentException("模型能力验证有效期必须大于零");
        }
        this.capabilityVerificationValidity = capabilityVerificationValidity;
    }
}
