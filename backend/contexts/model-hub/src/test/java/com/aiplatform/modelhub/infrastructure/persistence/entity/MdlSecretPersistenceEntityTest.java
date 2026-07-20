package com.aiplatform.modelhub.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aiplatform.modelhub.infrastructure.secret.ModelSecretMaterial;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MdlSecretPersistenceEntityTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-27T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void secretEntityMapsEncryptedMaterialAndOperatorAuditFields() {
        MdlSecretPersistenceEntity entity = MdlSecretPersistenceEntity.fromMaterial(
                "2001",
                "3001",
                "1001",
                new ModelSecretMaterial(
                        "ciphertext", "digest", "sk-***tail", "kms-main", "AES-256-GCM", 2
                ),
                FIXED_CLOCK
        );

        assertThat(entity.getTenantId()).isEqualTo(2001L);
        assertThat(entity.getProviderId()).isEqualTo(3001L);
        assertThat(entity.getSecretName()).isEqualTo("provider-3001-credential");
        assertThat(entity.getSecretCiphertext()).isEqualTo("ciphertext");
        assertThat(entity.getSecretDigest()).isEqualTo("digest");
        assertThat(entity.getSecretMaskedValue()).isEqualTo("sk-***tail");
        assertThat(entity.getKmsKeyId()).isEqualTo("kms-main");
        assertThat(entity.getCipherAlgorithm()).isEqualTo("AES-256-GCM");
        assertThat(entity.getCipherVersion()).isEqualTo(2);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getCreatedBy()).isEqualTo(1001L);
        assertThat(entity.getUpdatedBy()).isEqualTo(1001L);
        assertThat(entity.getRotatedAt()).isEqualTo(Instant.parse("2026-05-27T08:00:00Z"));
    }

    @Test
    void secretEntityRejectsNonNumericOperatorIdWithChineseMessage() {
        assertThatThrownBy(() -> MdlSecretPersistenceEntity.fromMaterial(
                        "2001",
                        "3001",
                        "operator",
                        new ModelSecretMaterial(
                                "ciphertext", "digest", "sk-***tail", "kms-main", "AES-256-GCM", 2
                        ),
                        FIXED_CLOCK
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作人 ID 必须是数字型数据库主键");
    }
}
