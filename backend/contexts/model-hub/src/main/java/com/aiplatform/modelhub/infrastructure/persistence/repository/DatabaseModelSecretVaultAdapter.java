package com.aiplatform.modelhub.infrastructure.persistence.repository;

import com.aiplatform.modelhub.domain.ModelSecretRef;
import com.aiplatform.modelhub.domain.port.ModelSecretVaultPort;
import com.aiplatform.modelhub.infrastructure.persistence.entity.MdlSecretPersistenceEntity;
import com.aiplatform.modelhub.infrastructure.persistence.mapper.MdlSecretMapper;
import com.aiplatform.modelhub.infrastructure.secret.ModelSecretCryptoService;
import com.aiplatform.modelhub.infrastructure.secret.ModelSecretMaterial;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 模型供应商凭证材料保管适配器，先加密和摘要化，再写入 `mdl_secret`。
 */
@Component
public class DatabaseModelSecretVaultAdapter implements ModelSecretVaultPort {
    private final MdlSecretMapper mapper;
    private final ModelSecretCryptoService cryptoService;
    private final Clock clock;

    public DatabaseModelSecretVaultAdapter(
            MdlSecretMapper mapper,
            ModelSecretCryptoService cryptoService,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "模型供应商凭证材料 Mapper 不能为空");
        this.cryptoService = Objects.requireNonNull(cryptoService, "模型供应商凭证材料加密服务不能为空");
        this.clock = Objects.requireNonNull(clock, "系统时钟不能为空");
    }

    @Override
    public ModelSecretRef store(
            TenantId tenantId,
            String providerId,
            String plainCredentialMaterial,
            String operatorId
    ) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        ModelSecretMaterial material = cryptoService.encrypt(plainCredentialMaterial);
        MdlSecretPersistenceEntity entity = MdlSecretPersistenceEntity.fromMaterial(
                tenantId.value(),
                providerId,
                operatorId,
                material,
                clock
        );
        requiredInsertedRows(mapper.insert(entity), "模型供应商凭证材料");
        return new ModelSecretRef(requiredSecretId(entity.getId()), material.maskedValue());
    }

    private static String requiredSecretId(Long id) {
        if (id == null) {
            throw new IllegalStateException("模型供应商凭证材料保存失败：数据库未返回凭证材料 ID");
        }
        return String.valueOf(id);
    }

    private static void requiredInsertedRows(int inserted, String fieldName) {
        if (inserted != 1) {
            throw new IllegalStateException(fieldName + "保存失败：数据库未写入记录");
        }
    }
}
