package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 加密模型凭据的 MyBatis 映射器，密文材料不离开持久化适配器。
 */
public interface ModelConfigurationCredentialMapper {

    void upsert(
            @Param("modelConfigurationId") long modelConfigurationId,
            @Param("algorithm") String algorithm,
            @Param("masterKeyVersion") String masterKeyVersion,
            @Param("dataKeyNonce") byte[] dataKeyNonce,
            @Param("encryptedDataKey") byte[] encryptedDataKey,
            @Param("ciphertextNonce") byte[] ciphertextNonce,
            @Param("ciphertext") byte[] ciphertext,
            @Param("fingerprint") String fingerprint,
            @Param("rotatedAt") Instant rotatedAt);

    CredentialRow findByConfigurationId(@Param("modelConfigurationId") long modelConfigurationId);

    boolean existsByConfigurationId(@Param("modelConfigurationId") long modelConfigurationId);

    record CredentialRow(
            String algorithm,
            String masterKeyVersion,
            byte[] dataKeyNonce,
            byte[] encryptedDataKey,
            byte[] ciphertextNonce,
            byte[] ciphertext) {
    }
}
