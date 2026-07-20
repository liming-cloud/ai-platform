package com.aiplatform.modelhub.domain.port;

import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.shared.kernel.TenantId;

/**
 * 模型供应商凭证材料保管端口，基础设施实现负责加密、脱敏和轮换。
 */
public interface ModelSecretVaultPort {
    ModelSecretRef store(TenantId tenantId, String providerId, String plainCredentialMaterial, String operatorId);
}
