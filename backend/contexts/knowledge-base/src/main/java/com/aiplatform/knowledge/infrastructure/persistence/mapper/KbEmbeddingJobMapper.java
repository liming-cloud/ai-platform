package com.aiplatform.knowledge.infrastructure.persistence.mapper;

import com.aiplatform.knowledge.infrastructure.persistence.entity.KbEmbeddingJobPersistenceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbEmbeddingJobMapper extends BaseMapper<KbEmbeddingJobPersistenceEntity> {
    Long nextJobId();

    List<KbEmbeddingJobPersistenceEntity> findLatestSnapshots(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("documentIds") List<Long> documentIds
    );

    KbEmbeddingJobPersistenceEntity claimNext(
            @Param("workerId") String workerId,
            @Param("leaseToken") String leaseToken,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("maxAttempts") int maxAttempts
    );

    KbEmbeddingJobPersistenceEntity cancelLatest(
            @Param("tenantId") long tenantId,
            @Param("knowledgeBaseId") long knowledgeBaseId,
            @Param("documentId") long documentId,
            @Param("reason") String reason,
            @Param("operatorId") long operatorId,
            @Param("traceId") String traceId,
            @Param("now") Instant now
    );

    int retryFailedManually(
            @Param("tenantId") long tenantId,
            @Param("jobId") long jobId,
            @Param("now") Instant now,
            @Param("operatorId") long operatorId
    );

    Long lockOwned(
            @Param("tenantId") long tenantId,
            @Param("jobId") long jobId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("now") Instant now
    );

    int renewOwned(
            @Param("tenantId") long tenantId,
            @Param("jobId") long jobId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("now") Instant now
    );

    int completeOwned(
            @Param("tenantId") long tenantId,
            @Param("jobId") long jobId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("now") Instant now
    );

    int retryOwned(
            @Param("tenantId") long tenantId,
            @Param("jobId") long jobId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("nextRetryAt") Instant nextRetryAt,
            @Param("now") Instant now
    );

    int failOwned(
            @Param("tenantId") long tenantId,
            @Param("jobId") long jobId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseToken") String leaseToken,
            @Param("fenceVersion") long fenceVersion,
            @Param("failureCode") String failureCode,
            @Param("failureSummary") String failureSummary,
            @Param("now") Instant now
    );
}
