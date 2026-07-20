package com.aiplatform.appstudio.infrastructure.persistence.repository;

import com.aiplatform.appstudio.domain.port.chat.ModelInvocationAuditPort;
import com.aiplatform.appstudio.domain.port.chat.model.ModelInvocationAuditRecord;
import com.aiplatform.appstudio.infrastructure.persistence.entity.ModelInvocationLogPersistenceEntity;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.ModelInvocationLogMapper;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * 模型调用审计仓储适配器，写入平台统一的模型调用日志表。
 */
@Repository
public class ModelInvocationLogRepositoryAdapter implements ModelInvocationAuditPort {
    private final ModelInvocationLogMapper mapper;

    public ModelInvocationLogRepositoryAdapter(ModelInvocationLogMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型调用日志 Mapper 不能为空");
    }

    @Override
    public String record(ModelInvocationAuditRecord record) {
        ModelInvocationLogPersistenceEntity entity = ModelInvocationLogPersistenceEntity.fromRecord(record);
        if (mapper.insert(entity) != 1) {
            throw new IllegalStateException("模型调用日志保存失败");
        }
        if (entity.getId() == null) {
            throw new IllegalStateException("模型调用日志 ID 回填失败");
        }
        return String.valueOf(entity.getId());
    }
}
