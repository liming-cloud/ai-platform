package com.aiplatform.foundation.modelruntime.infrastructure.persistence.provider.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 厂商连接的私有密文凭据持久化实体；绝不用于 HTTP 或领域模型。
 */
@TableName("model_runtime.provider_connection_credential")
public class ProviderConnectionCredentialEntity {

    @TableId(value = "provider_connection_id", type = IdType.INPUT)
    private Long providerConnectionId;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("algorithm")
    private String algorithm;
    @TableField("master_key_version")
    private String masterKeyVersion;
    @TableField("data_key_nonce")
    private byte[] dataKeyNonce;
    @TableField("encrypted_data_key")
    private byte[] encryptedDataKey;
    @TableField("ciphertext_nonce")
    private byte[] ciphertextNonce;
    @TableField("ciphertext")
    private byte[] ciphertext;
    @TableField("fingerprint")
    private String fingerprint;
    @TableField("rotated_at")
    private Instant rotatedAt;

    public Long getProviderConnectionId() { return providerConnectionId; }
    public void setProviderConnectionId(Long value) { providerConnectionId = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String value) { algorithm = value; }
    public String getMasterKeyVersion() { return masterKeyVersion; }
    public void setMasterKeyVersion(String value) { masterKeyVersion = value; }
    public byte[] getDataKeyNonce() { return dataKeyNonce; }
    public void setDataKeyNonce(byte[] value) { dataKeyNonce = value; }
    public byte[] getEncryptedDataKey() { return encryptedDataKey; }
    public void setEncryptedDataKey(byte[] value) { encryptedDataKey = value; }
    public byte[] getCiphertextNonce() { return ciphertextNonce; }
    public void setCiphertextNonce(byte[] value) { ciphertextNonce = value; }
    public byte[] getCiphertext() { return ciphertext; }
    public void setCiphertext(byte[] value) { ciphertext = value; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant value) { rotatedAt = value; }
}
