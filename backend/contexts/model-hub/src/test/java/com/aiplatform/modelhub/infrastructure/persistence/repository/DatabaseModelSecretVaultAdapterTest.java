package com.aiplatform.modelhub.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import com.aiplatform.modelhub.infrastructure.secret.ModelSecretCryptoService;
import com.aiplatform.modelhub.infrastructure.secret.ModelSecretMaterial;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatabaseModelSecretVaultAdapterTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-29T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void storeReturnsPersistedSecretReferenceAfterDatabaseIdBackfill() {
        MdlSecretMapper mapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = mock(ModelSecretCryptoService.class);
        when(cryptoService.encrypt("sk-live-plain-secret"))
                .thenReturn(material());
        when(mapper.insert(any(MdlSecretPersistenceEntity.class))).thenAnswer(invocation -> {
            MdlSecretPersistenceEntity entity = invocation.getArgument(0);
            entity.setId(9001L);
            return 1;
        });
        DatabaseModelSecretVaultAdapter adapter = new DatabaseModelSecretVaultAdapter(
                mapper,
                cryptoService,
                FIXED_CLOCK
        );

        ModelSecretRef ref = adapter.store(
                TenantId.of("2001"),
                "3001",
                "sk-live-plain-secret",
                "1001"
        );

        assertThat(ref.secretId()).isEqualTo("9001");
        assertThat(ref.maskedValue()).isEqualTo("sk-***cret");
        ArgumentCaptor<MdlSecretPersistenceEntity> entityCaptor =
                ArgumentCaptor.forClass(MdlSecretPersistenceEntity.class);
        verify(mapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getTenantId()).isEqualTo(2001L);
        assertThat(entityCaptor.getValue().getProviderId()).isEqualTo(3001L);
        assertThat(entityCaptor.getValue().getCreatedBy()).isEqualTo(1001L);
        assertThat(entityCaptor.getValue().getCipherAlgorithm()).isEqualTo("AES-256-GCM");
        assertThat(entityCaptor.getValue().getCipherVersion()).isEqualTo(2);
    }

    @Test
    void storeFailsClosedWhenInsertedSecretIdMissing() {
        MdlSecretMapper mapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = mock(ModelSecretCryptoService.class);
        when(cryptoService.encrypt("sk-live-plain-secret"))
                .thenReturn(material());
        when(mapper.insert(any(MdlSecretPersistenceEntity.class))).thenReturn(1);
        DatabaseModelSecretVaultAdapter adapter = new DatabaseModelSecretVaultAdapter(
                mapper,
                cryptoService,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> adapter.store(
                TenantId.of("2001"),
                "3001",
                "sk-live-plain-secret",
                "1001"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商凭证材料保存失败：数据库未返回凭证材料 ID");
    }

    @Test
    void storeFailsClosedWhenInsertAffectsNoRows() {
        MdlSecretMapper mapper = mock(MdlSecretMapper.class);
        ModelSecretCryptoService cryptoService = mock(ModelSecretCryptoService.class);
        when(cryptoService.encrypt("sk-live-plain-secret"))
                .thenReturn(material());
        when(mapper.insert(any(MdlSecretPersistenceEntity.class))).thenReturn(0);
        DatabaseModelSecretVaultAdapter adapter = new DatabaseModelSecretVaultAdapter(
                mapper,
                cryptoService,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> adapter.store(
                TenantId.of("2001"),
                "3001",
                "sk-live-plain-secret",
                "1001"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型供应商凭证材料保存失败：数据库未写入记录");
    }

    private static ModelSecretMaterial material() {
        return new ModelSecretMaterial(
                "ciphertext", "digest", "sk-***cret", "kms-main", "AES-256-GCM", 2
        );
    }
}
