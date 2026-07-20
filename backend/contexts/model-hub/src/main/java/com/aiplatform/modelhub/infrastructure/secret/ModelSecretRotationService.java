package com.aiplatform.modelhub.infrastructure.secret;

import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 以有界批次和完整旧状态 CAS 在线重加密模型凭证。
 *
 * <p>并发 worker 可以读取同一候选，但 tenant/id/version/旧 metadata fencing 确保只有一方提交。
 * 随机密文重复计算不会造成双写；已完成行不再进入 backlog。</p>
 */
@Component
public class ModelSecretRotationService {
    private final MdlSecretMapper mapper;
    private final ModelSecretCryptoService cryptoService;
    private final Clock clock;
    private final int batchSize;
    private final AtomicLong candidateCursor = new AtomicLong();

    public ModelSecretRotationService(
            MdlSecretMapper mapper,
            ModelSecretCryptoService cryptoService,
            Clock clock,
            @Value("${aip.model-hub.secret.rotation.batch-size:20}") int batchSize
    ) {
        this.mapper = Objects.requireNonNull(mapper, "模型供应商凭证材料 Mapper 不能为空");
        this.cryptoService = Objects.requireNonNull(cryptoService, "模型供应商凭证材料加解密服务不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("模型密钥轮换 batch size 必须位于 1..100");
        }
        this.batchSize = batchSize;
    }

    public synchronized ModelSecretRotationBatchResult rotateBatch() {
        List<MdlSecretPersistenceEntity> candidates = mapper.findRotationCandidates(
                cryptoService.activeKeyId(),
                cryptoService.targetAlgorithm(),
                cryptoService.targetVersion(),
                candidateCursor.get(),
                batchSize
        );
        if (candidates == null) {
            throw new IllegalStateException("模型密钥轮换候选结果不能为空");
        }
        int rotated = 0;
        int conflicted = 0;
        int failed = 0;
        for (MdlSecretPersistenceEntity candidate : candidates) {
            try {
                MdlSecretPersistenceEntity row = Objects.requireNonNull(candidate, "模型密钥轮换候选不能为空");
                candidateCursor.set(requirePositive(row.getId(), "凭证材料 ID"));
                if (rotateOne(row)) {
                    rotated++;
                } else {
                    conflicted++;
                }
            } catch (RuntimeException exception) {
                // 失败计数是唯一出站事实；不得记录 ciphertext、凭证明文或异常 message。
                failed++;
            }
        }
        return new ModelSecretRotationBatchResult(
                candidates.size(),
                rotated,
                conflicted,
                failed,
                backlog()
        );
    }

    public long backlog() {
        long count = mapper.countRotationBacklog(
                cryptoService.activeKeyId(),
                cryptoService.targetAlgorithm(),
                cryptoService.targetVersion()
        );
        if (count < 0) {
            throw new IllegalStateException("模型密钥轮换 backlog 结果不合法");
        }
        return count;
    }

    public String activeKeyId() {
        return cryptoService.activeKeyId();
    }

    private boolean rotateOne(MdlSecretPersistenceEntity row) {
        Long id = requirePositive(row.getId(), "凭证材料 ID");
        Long tenantId = requirePositive(row.getTenantId(), "租户 ID");
        Integer version = requireNonNegative(row.getVersion(), "凭证材料乐观锁版本");
        String oldKeyId = requireText(row.getKmsKeyId(), "旧 KMS 密钥 ID");
        String oldAlgorithm = requireText(row.getCipherAlgorithm(), "旧加密算法");
        Integer oldCipherVersion = requirePositive(row.getCipherVersion(), "旧密文版本");
        String plaintext = cryptoService.decrypt(
                row.getSecretCiphertext(),
                row.getSecretDigest(),
                oldKeyId,
                oldAlgorithm,
                oldCipherVersion
        );
        ModelSecretMaterial rotated = cryptoService.encrypt(plaintext);
        if (!Objects.equals(row.getSecretDigest(), rotated.digest())) {
            throw new IllegalStateException("模型供应商凭证材料完整性校验失败");
        }
        Instant now = clock.instant();
        int affected = mapper.rotateIfUnchanged(
                id,
                tenantId,
                version,
                oldKeyId,
                oldAlgorithm,
                oldCipherVersion,
                rotated.ciphertext(),
                rotated.kmsKeyId(),
                rotated.cipherAlgorithm(),
                rotated.cipherVersion(),
                now
        );
        if (affected < 0 || affected > 1) {
            throw new IllegalStateException("模型密钥轮换 CAS 返回值不合法");
        }
        return affected == 1;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "不能为空");
        }
        return value;
    }

    private static <T extends Number> T requirePositive(T value, String fieldName) {
        if (value == null || value.longValue() < 1) {
            throw new IllegalStateException(fieldName + "必须为正整数");
        }
        return value;
    }

    private static Integer requireNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalStateException(fieldName + "不能为负数");
        }
        return value;
    }
}
