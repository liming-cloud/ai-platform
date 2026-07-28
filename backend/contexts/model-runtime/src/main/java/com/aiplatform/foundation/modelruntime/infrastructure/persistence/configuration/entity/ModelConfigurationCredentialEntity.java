package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 加密模型凭据表元数据实体；仅持久化适配器可以接触密文材料。
 */
@TableName("model_runtime.model_configuration_credential")
public class ModelConfigurationCredentialEntity {

    @TableId(value = "model_configuration_id", type = IdType.INPUT) private Long modelConfigurationId;
    private String algorithm;
    @TableField("master_key_version") private String masterKeyVersion;
    @TableField("data_key_nonce") private byte[] dataKeyNonce;
    @TableField("encrypted_data_key") private byte[] encryptedDataKey;
    @TableField("ciphertext_nonce") private byte[] ciphertextNonce;
    private byte[] ciphertext;
    private String fingerprint;
    @TableField("rotated_at") private Instant rotatedAt;

    public Long getModelConfigurationId() { return modelConfigurationId; }
    public void setModelConfigurationId(Long modelConfigurationId) { this.modelConfigurationId = modelConfigurationId; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public String getMasterKeyVersion() { return masterKeyVersion; }
    public void setMasterKeyVersion(String masterKeyVersion) { this.masterKeyVersion = masterKeyVersion; }
    public byte[] getDataKeyNonce() { return dataKeyNonce; }
    public void setDataKeyNonce(byte[] dataKeyNonce) { this.dataKeyNonce = dataKeyNonce; }
    public byte[] getEncryptedDataKey() { return encryptedDataKey; }
    public void setEncryptedDataKey(byte[] encryptedDataKey) { this.encryptedDataKey = encryptedDataKey; }
    public byte[] getCiphertextNonce() { return ciphertextNonce; }
    public void setCiphertextNonce(byte[] ciphertextNonce) { this.ciphertextNonce = ciphertextNonce; }
    public byte[] getCiphertext() { return ciphertext; }
    public void setCiphertext(byte[] ciphertext) { this.ciphertext = ciphertext; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant rotatedAt) { this.rotatedAt = rotatedAt; }
}
