package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.mapper;

import com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.entity.ProviderConnectionCredentialEntity;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/**
 * 厂商连接密文凭据的 MyBatis-Plus 映射器。
 */
public interface ProviderConnectionCredentialMapper {

    void upsert(
            @Param("providerConnectionId") long providerConnectionId,
            @Param("tenantId") long tenantId,
            @Param("algorithm") String algorithm,
            @Param("masterKeyVersion") String masterKeyVersion,
            @Param("dataKeyNonce") byte[] dataKeyNonce,
            @Param("encryptedDataKey") byte[] encryptedDataKey,
            @Param("ciphertextNonce") byte[] ciphertextNonce,
            @Param("ciphertext") byte[] ciphertext,
            @Param("fingerprint") String fingerprint,
            @Param("rotatedAt") Instant rotatedAt);

    ProviderConnectionCredentialEntity selectByConnectionId(
            @Param("providerConnectionId") long providerConnectionId);
}
