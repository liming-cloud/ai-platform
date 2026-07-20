package com.aiplatform.summary.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.contracts.capability.summary.SummaryMetricFreshness;
import com.aiplatform.contracts.capability.summary.SummaryMetricScope;
import com.aiplatform.summary.domain.SummaryMetricSnapshot;
import com.aiplatform.summary.domain.port.SummaryMetricSnapshotRepository;
import com.aiplatform.summary.infrastructure.persistence.entity.SummaryMetricSnapshotPersistenceEntity;
import com.aiplatform.summary.infrastructure.persistence.mapper.SummaryMetricSnapshotMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseSummaryMetricSnapshotRepository implements SummaryMetricSnapshotRepository {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };

    private final SummaryMetricSnapshotMapper mapper;
    private final JsonMapper objectMapper;

    public DatabaseSummaryMetricSnapshotRepository(SummaryMetricSnapshotMapper mapper, JsonMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "指标快照 Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    public void save(SummaryMetricSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "指标快照不能为空");
        SummaryMetricSnapshotPersistenceEntity entity = new SummaryMetricSnapshotPersistenceEntity();
        entity.setTenantId(parseRequiredLong(snapshot.tenantId(), "tenantId"));
        entity.setScopeType(snapshot.scope().name());
        entity.setMetricCode(snapshot.metricCode());
        entity.setMetricPayload(writePayload(snapshot.payload()));
        entity.setFreshness(snapshot.freshness().name());
        entity.setPartial(snapshot.partial());
        entity.setFailedReason(snapshot.failedReason());
        entity.setAsOf(snapshot.asOf());
        int affected = mapper.upsert(entity);
        if (affected != 1) {
            throw new IllegalStateException("指标快照保存失败，影响行数：" + affected);
        }
    }

    @Override
    public List<SummaryMetricSnapshot> findByScope(String tenantId, SummaryMetricScope scope) {
        List<SummaryMetricSnapshotPersistenceEntity> rows = mapper.findByScope(
                parseRequiredLong(tenantId, "tenantId"),
                Objects.requireNonNull(scope, "指标范围不能为空").name()
        );
        if (rows == null) {
            throw new IllegalStateException("指标快照查询结果不能为空");
        }
        return rows.stream().map(this::toDomain).toList();
    }

    private SummaryMetricSnapshot toDomain(SummaryMetricSnapshotPersistenceEntity row) {
        return new SummaryMetricSnapshot(
                String.valueOf(row.getTenantId()),
                SummaryMetricScope.valueOf(row.getScopeType()),
                row.getMetricCode(),
                readPayload(row.getMetricPayload()),
                SummaryMetricFreshness.valueOf(row.getFreshness()),
                Boolean.TRUE.equals(row.getPartial()),
                row.getFailedReason(),
                row.getAsOf()
        );
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("指标载荷序列化失败", exception);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("指标载荷反序列化失败", exception);
        }
    }
}
