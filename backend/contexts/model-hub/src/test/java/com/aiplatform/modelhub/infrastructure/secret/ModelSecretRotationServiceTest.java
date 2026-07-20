package com.aiplatform.modelhub.infrastructure.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelSecretRotationServiceTest {
    private static final String OLD_KEY_ID = "kms-old-2025";
    private static final String OLD_KEY = "o".repeat(32);
    private static final String ACTIVE_KEY_ID = "kms-active-2026";
    private static final String ACTIVE_KEY = "a".repeat(32);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-18T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void rotatesLegacySecretWithTenantVersionAndOldMetadataCasThenConvergesIdempotently() {
        MdlSecretMapper mapper = mock(MdlSecretMapper.class);
        MdlSecretPersistenceEntity candidate = legacyCandidate();
        when(mapper.findRotationCandidates(anyString(), anyString(), eq(2), eq(0L), eq(20)))
                .thenReturn(List.of(candidate));
        when(mapper.findRotationCandidates(anyString(), anyString(), eq(2), eq(9001L), eq(20)))
                .thenReturn(List.of());
        when(mapper.rotateIfUnchanged(
                eq(9001L), eq(2001L), eq(7), eq(OLD_KEY_ID), eq("AES-256-GCM"), eq(1),
                anyString(), eq(ACTIVE_KEY_ID), eq("AES-256-GCM"), eq(2), eq(CLOCK.instant())
        )).thenReturn(1);
        when(mapper.countRotationBacklog(anyString(), anyString(), eq(2))).thenReturn(0L);
        ModelSecretRotationService service = service(mapper);

        ModelSecretRotationBatchResult first = service.rotateBatch();
        ModelSecretRotationBatchResult second = service.rotateBatch();

        assertThat(first).isEqualTo(new ModelSecretRotationBatchResult(1, 1, 0, 0, 0));
        assertThat(second).isEqualTo(new ModelSecretRotationBatchResult(0, 0, 0, 0, 0));
        ArgumentCaptor<String> ciphertextCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).rotateIfUnchanged(
                eq(9001L), eq(2001L), eq(7), eq(OLD_KEY_ID), eq("AES-256-GCM"), eq(1),
                ciphertextCaptor.capture(), eq(ACTIVE_KEY_ID), eq("AES-256-GCM"), eq(2), eq(CLOCK.instant())
        );
        ModelSecretCryptoService activeOnly = new ModelSecretCryptoService(ACTIVE_KEY_ID, ACTIVE_KEY);
        assertThat(activeOnly.decrypt(ciphertextCaptor.getValue())).isEqualTo("sk-legacy-secret");
    }

    @Test
    void reportsSafeConflictWhenAnotherWorkerWinsCas() {
        MdlSecretMapper mapper = mock(MdlSecretMapper.class);
        when(mapper.findRotationCandidates(anyString(), anyString(), eq(2), eq(0L), eq(20)))
                .thenReturn(List.of(legacyCandidate()));
        when(mapper.rotateIfUnchanged(
                eq(9001L), eq(2001L), eq(7), eq(OLD_KEY_ID), eq("AES-256-GCM"), eq(1),
                anyString(), eq(ACTIVE_KEY_ID), eq("AES-256-GCM"), eq(2), eq(CLOCK.instant())
        )).thenReturn(0);
        when(mapper.countRotationBacklog(anyString(), anyString(), eq(2))).thenReturn(1L);

        ModelSecretRotationBatchResult result = service(mapper).rotateBatch();

        assertThat(result).isEqualTo(new ModelSecretRotationBatchResult(1, 0, 1, 0, 1));
    }

    @Test
    void failsClosedPerRowWhenPreviousKeyIsUnavailableWithoutWritingCiphertext() {
        MdlSecretMapper mapper = mock(MdlSecretMapper.class);
        MdlSecretPersistenceEntity candidate = legacyCandidate();
        candidate.setKmsKeyId("kms-unavailable");
        when(mapper.findRotationCandidates(anyString(), anyString(), eq(2), eq(0L), eq(20)))
                .thenReturn(List.of(candidate));
        when(mapper.countRotationBacklog(anyString(), anyString(), eq(2))).thenReturn(1L);

        ModelSecretRotationBatchResult result = service(mapper).rotateBatch();

        assertThat(result).isEqualTo(new ModelSecretRotationBatchResult(1, 0, 0, 1, 1));
        verify(mapper, never()).rotateIfUnchanged(
                eq(9001L), eq(2001L), eq(7), anyString(), anyString(), eq(1),
                anyString(), anyString(), anyString(), eq(2), eq(CLOCK.instant())
        );
    }

    @Test
    void advancesTheCyclicCursorSoOneBrokenRowCannotStarveLaterCredentials() {
        MdlSecretMapper mapper = mock(MdlSecretMapper.class);
        MdlSecretPersistenceEntity broken = legacyCandidate();
        broken.setKmsKeyId("kms-unavailable");
        MdlSecretPersistenceEntity healthy = legacyCandidate();
        healthy.setId(9002L);
        when(mapper.findRotationCandidates(anyString(), anyString(), eq(2), eq(0L), eq(20)))
                .thenReturn(List.of(broken));
        when(mapper.findRotationCandidates(anyString(), anyString(), eq(2), eq(9001L), eq(20)))
                .thenReturn(List.of(healthy));
        when(mapper.rotateIfUnchanged(
                eq(9002L), eq(2001L), eq(7), eq(OLD_KEY_ID), eq("AES-256-GCM"), eq(1),
                anyString(), eq(ACTIVE_KEY_ID), eq("AES-256-GCM"), eq(2), eq(CLOCK.instant())
        )).thenReturn(1);
        when(mapper.countRotationBacklog(anyString(), anyString(), eq(2))).thenReturn(2L, 1L);
        ModelSecretRotationService service = service(mapper);

        ModelSecretRotationBatchResult first = service.rotateBatch();
        ModelSecretRotationBatchResult second = service.rotateBatch();

        assertThat(first).isEqualTo(new ModelSecretRotationBatchResult(1, 0, 0, 1, 2));
        assertThat(second).isEqualTo(new ModelSecretRotationBatchResult(1, 1, 0, 0, 1));
        verify(mapper).findRotationCandidates(anyString(), anyString(), eq(2), eq(9001L), eq(20));
    }

    private static ModelSecretRotationService service(MdlSecretMapper mapper) {
        return new ModelSecretRotationService(
                mapper,
                new ModelSecretCryptoService(ACTIVE_KEY_ID, ACTIVE_KEY, OLD_KEY_ID + "=" + OLD_KEY),
                CLOCK,
                20
        );
    }

    private static MdlSecretPersistenceEntity legacyCandidate() {
        String plaintext = "sk-legacy-secret";
        MdlSecretPersistenceEntity row = new MdlSecretPersistenceEntity();
        row.setId(9001L);
        row.setTenantId(2001L);
        row.setSecretCiphertext(legacyEncrypt(plaintext));
        row.setSecretDigest(sha256Hex(plaintext));
        row.setKmsKeyId(OLD_KEY_ID);
        row.setCipherAlgorithm("AES-256-GCM");
        row.setCipherVersion(1);
        row.setStatus("ACTIVE");
        row.setVersion(7);
        return row;
    }

    private static String legacyEncrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                            .digest(OLD_KEY.getBytes(StandardCharsets.UTF_8)), "AES"),
                    new GCMParameterSpec(128, iv)
            );
            return "aes-gcm:v1:" + Base64.getEncoder().encodeToString(iv) + "."
                    + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }
}
