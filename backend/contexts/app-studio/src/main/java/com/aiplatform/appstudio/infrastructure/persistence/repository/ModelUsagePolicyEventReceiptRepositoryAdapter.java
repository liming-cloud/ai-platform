package com.aiplatform.appstudio.infrastructure.persistence.repository;

import com.aiplatform.appstudio.domain.port.modelpolicy.ModelUsagePolicyEventReceiptRepository;
import com.aiplatform.appstudio.infrastructure.persistence.mapper.ModelUsagePolicyEventReceiptMapper;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class ModelUsagePolicyEventReceiptRepositoryAdapter implements ModelUsagePolicyEventReceiptRepository {
    private final ModelUsagePolicyEventReceiptMapper mapper;

    public ModelUsagePolicyEventReceiptRepositoryAdapter(ModelUsagePolicyEventReceiptMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型策略事件消费凭据 Mapper 不能为空");
    }

    @Override
    public boolean recordIfAbsent(String eventId, long eventVersion, Instant occurredAt) {
        String normalizedEventId = requireText(eventId, "事件 ID");
        if (eventVersion <= 0) {
            throw new IllegalArgumentException("事件版本必须大于 0");
        }
        Objects.requireNonNull(occurredAt, "事件发生时间不能为空");
        int inserted = mapper.insertIfAbsent(normalizedEventId, eventVersion, occurredAt);
        if (inserted == 1) {
            return true;
        }
        if (inserted == 0) {
            return false;
        }
        throw new IllegalStateException("模型策略事件消费凭据写入结果非法");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
