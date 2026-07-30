package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.repository;

import com.aiplatform.foundation.trustedquery.domain.query.feedback.model.QueryFeedback;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.policy.QueryFeedbackRecording;
import com.aiplatform.foundation.trustedquery.domain.query.feedback.repository.QueryFeedbackRepository;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity.QueryFeedbackEntity;
import com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.mapper.QueryFeedbackMapper;
import java.util.Objects;

/**
 * PostgreSQL 查询反馈原子写入实现。
 */
public final class PostgresQueryFeedbackRepository implements QueryFeedbackRepository {

    private final QueryFeedbackMapper mapper;

    public PostgresQueryFeedbackRepository(QueryFeedbackMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "查询反馈映射器不能为空");
    }

    @Override
    public QueryFeedbackRecording record(QueryFeedback feedback) {
        Objects.requireNonNull(feedback, "查询反馈不能为空");
        return mapper.insertIfAbsent(toEntity(feedback)) == 1
                ? QueryFeedbackRecording.recorded()
                : QueryFeedbackRecording.duplicate();
    }

    private static QueryFeedbackEntity toEntity(QueryFeedback feedback) {
        var entity = new QueryFeedbackEntity();
        entity.setId(feedback.id().value());
        entity.setTenantId(feedback.tenantId().value());
        entity.setQueryRunId(feedback.queryRunId().value());
        entity.setRequestedBy(feedback.requestedBy().value());
        entity.setOutcome(feedback.outcome().name());
        entity.setReason(feedback.reason().name());
        entity.setCreatedAt(feedback.createdAt());
        return entity;
    }
}
