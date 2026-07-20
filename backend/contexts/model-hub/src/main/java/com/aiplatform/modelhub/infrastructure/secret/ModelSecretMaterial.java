package com.aiplatform.modelhub.infrastructure.secret;

import java.util.Objects;

/**
 * 加密后的模型供应商凭证材料，只允许基础设施层持久化密文、摘要和脱敏值。
 */
public record ModelSecretMaterial(
        String ciphertext,
        String digest,
        String maskedValue,
        String kmsKeyId,
        String cipherAlgorithm,
        int cipherVersion
) {
    public ModelSecretMaterial {
        Objects.requireNonNull(ciphertext, "凭证材料密文不能为空");
        Objects.requireNonNull(digest, "凭证材料摘要不能为空");
        Objects.requireNonNull(maskedValue, "凭证材料脱敏值不能为空");
        Objects.requireNonNull(kmsKeyId, "KMS 密钥 ID 不能为空");
        Objects.requireNonNull(cipherAlgorithm, "凭证材料加密算法不能为空");
        if (cipherVersion < 1) {
            throw new IllegalArgumentException("凭证材料加密版本必须为正整数");
        }
    }
}
