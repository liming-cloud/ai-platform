package com.aiplatform.modelhub.infrastructure.persistence.entity;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.modelhub.infrastructure.secret.ModelSecretMaterial;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * `mdl_secret` 表持久化实体，仅保存凭证材料密文、摘要、脱敏值和 KMS Key 引用。
 */
@TableName("mdl_secret")
public class MdlSecretPersistenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("provider_id")
    private Long providerId;

    @TableField("secret_name")
    private String secretName;

    @TableField("secret_ciphertext")
    private String secretCiphertext;

    @TableField("secret_digest")
    private String secretDigest;

    @TableField("secret_masked_value")
    private String secretMaskedValue;

    @TableField("kms_key_id")
    private String kmsKeyId;

    @TableField("cipher_algorithm")
    private String cipherAlgorithm;

    @TableField("cipher_version")
    private Integer cipherVersion;

    @TableField("status")
    private String status;

    @TableField("rotated_at")
    private Instant rotatedAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableField("deleted_at")
    private Instant deletedAt;

    @TableField("version")
    private Integer version;

    public static MdlSecretPersistenceEntity fromMaterial(
            String tenantId,
            String providerId,
            String operatorId,
            ModelSecretMaterial material,
            Clock clock
    ) {
        Objects.requireNonNull(material, "模型供应商凭证材料不能为空");
        Instant now = Objects.requireNonNull(clock, "系统时钟不能为空").instant();
        Long operatorNumericId = parseRequiredLong(operatorId, "操作人 ID");
        MdlSecretPersistenceEntity entity = new MdlSecretPersistenceEntity();
        entity.setTenantId(parseRequiredLong(tenantId, "租户 ID"));
        entity.setProviderId(parseRequiredLong(providerId, "模型供应商 ID"));
        entity.setSecretName("provider-" + providerId + "-credential");
        entity.setSecretCiphertext(material.ciphertext());
        entity.setSecretDigest(material.digest());
        entity.setSecretMaskedValue(material.maskedValue());
        entity.setKmsKeyId(material.kmsKeyId());
        entity.setCipherAlgorithm(material.cipherAlgorithm());
        entity.setCipherVersion(material.cipherVersion());
        entity.setStatus("ACTIVE");
        entity.setRotatedAt(now);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operatorNumericId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorNumericId);
        entity.setVersion(0);
        return entity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getSecretCiphertext() {
        return secretCiphertext;
    }

    public void setSecretCiphertext(String secretCiphertext) {
        this.secretCiphertext = secretCiphertext;
    }

    public String getSecretDigest() {
        return secretDigest;
    }

    public void setSecretDigest(String secretDigest) {
        this.secretDigest = secretDigest;
    }

    public String getSecretMaskedValue() {
        return secretMaskedValue;
    }

    public void setSecretMaskedValue(String secretMaskedValue) {
        this.secretMaskedValue = secretMaskedValue;
    }

    public String getKmsKeyId() {
        return kmsKeyId;
    }

    public void setKmsKeyId(String kmsKeyId) {
        this.kmsKeyId = kmsKeyId;
    }

    public String getCipherAlgorithm() {
        return cipherAlgorithm;
    }

    public void setCipherAlgorithm(String cipherAlgorithm) {
        this.cipherAlgorithm = cipherAlgorithm;
    }

    public Integer getCipherVersion() {
        return cipherVersion;
    }

    public void setCipherVersion(Integer cipherVersion) {
        this.cipherVersion = cipherVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(Instant rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
