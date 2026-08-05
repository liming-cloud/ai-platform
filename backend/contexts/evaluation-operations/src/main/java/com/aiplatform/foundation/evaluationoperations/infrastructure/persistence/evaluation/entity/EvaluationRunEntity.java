package com.aiplatform.foundation.evaluationoperations.infrastructure.persistence.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 评测运行终态的关系表实体；字段集只包含冻结快照、计数、指标和结论。
 */
@TableName("evaluation_operations.evaluation_run")
public class EvaluationRunEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id") private Long tenantId;
    @TableField("assistant_version_id") private Long assistantVersionId;
    @TableField("assistant_version_revision") private long assistantVersionRevision;
    @TableField("dataset_id") private Long datasetId;
    @TableField("dataset_version") private long datasetVersion;
    @TableField("dataset_content_digest") private String datasetContentDigest;
    @TableField("gate_policy_version") private String gatePolicyVersion;
    @TableField("minimum_recall_at_10") private double minimumRecallAt10;
    @TableField("minimum_citation_correctness") private double minimumCitationCorrectness;
    @TableField("minimum_correct_refusal_rate") private double minimumCorrectRefusalRate;
    @TableField("model_route_fingerprint") private String modelRouteFingerprint;
    @TableField("metrics_implementation_version") private String metricsImplementationVersion;
    @TableField("status") private String status;
    @TableField("expected_evidence_count") private int expectedEvidenceCount;
    @TableField("retrieved_expected_evidence_count") private int retrievedExpectedEvidenceCount;
    @TableField("citation_count") private int citationCount;
    @TableField("correct_citation_count") private int correctCitationCount;
    @TableField("expected_refusal_count") private int expectedRefusalCount;
    @TableField("correct_refusal_count") private int correctRefusalCount;
    @TableField("maximum_retrieved_candidate_count") private int maximumRetrievedCandidateCount;
    @TableField("failed_entry_count") private int failedEntryCount;
    @TableField("unauthorized_evidence") private boolean unauthorizedEvidence;
    @TableField("metrics_computable") private boolean metricsComputable;
    @TableField("recall_at_10") private double recallAt10;
    @TableField("citation_correctness") private double citationCorrectness;
    @TableField("correct_refusal_rate") private double correctRefusalRate;
    @TableField("version") private long version;
    @TableField("created_at") private Instant createdAt;
    @TableField("completed_at") private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(Long value) { assistantVersionId = value; }
    public long getAssistantVersionRevision() { return assistantVersionRevision; }
    public void setAssistantVersionRevision(long value) { assistantVersionRevision = value; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long value) { datasetId = value; }
    public long getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(long value) { datasetVersion = value; }
    public String getDatasetContentDigest() { return datasetContentDigest; }
    public void setDatasetContentDigest(String value) { datasetContentDigest = value; }
    public String getGatePolicyVersion() { return gatePolicyVersion; }
    public void setGatePolicyVersion(String value) { gatePolicyVersion = value; }
    public double getMinimumRecallAt10() { return minimumRecallAt10; }
    public void setMinimumRecallAt10(double value) { minimumRecallAt10 = value; }
    public double getMinimumCitationCorrectness() { return minimumCitationCorrectness; }
    public void setMinimumCitationCorrectness(double value) { minimumCitationCorrectness = value; }
    public double getMinimumCorrectRefusalRate() { return minimumCorrectRefusalRate; }
    public void setMinimumCorrectRefusalRate(double value) { minimumCorrectRefusalRate = value; }
    public String getModelRouteFingerprint() { return modelRouteFingerprint; }
    public void setModelRouteFingerprint(String value) { modelRouteFingerprint = value; }
    public String getMetricsImplementationVersion() { return metricsImplementationVersion; }
    public void setMetricsImplementationVersion(String value) { metricsImplementationVersion = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public int getExpectedEvidenceCount() { return expectedEvidenceCount; }
    public void setExpectedEvidenceCount(int value) { expectedEvidenceCount = value; }
    public int getRetrievedExpectedEvidenceCount() { return retrievedExpectedEvidenceCount; }
    public void setRetrievedExpectedEvidenceCount(int value) { retrievedExpectedEvidenceCount = value; }
    public int getCitationCount() { return citationCount; }
    public void setCitationCount(int value) { citationCount = value; }
    public int getCorrectCitationCount() { return correctCitationCount; }
    public void setCorrectCitationCount(int value) { correctCitationCount = value; }
    public int getExpectedRefusalCount() { return expectedRefusalCount; }
    public void setExpectedRefusalCount(int value) { expectedRefusalCount = value; }
    public int getCorrectRefusalCount() { return correctRefusalCount; }
    public void setCorrectRefusalCount(int value) { correctRefusalCount = value; }
    public int getMaximumRetrievedCandidateCount() { return maximumRetrievedCandidateCount; }
    public void setMaximumRetrievedCandidateCount(int value) { maximumRetrievedCandidateCount = value; }
    public int getFailedEntryCount() { return failedEntryCount; }
    public void setFailedEntryCount(int value) { failedEntryCount = value; }
    public boolean isUnauthorizedEvidence() { return unauthorizedEvidence; }
    public void setUnauthorizedEvidence(boolean value) { unauthorizedEvidence = value; }
    public boolean isMetricsComputable() { return metricsComputable; }
    public void setMetricsComputable(boolean value) { metricsComputable = value; }
    public double getRecallAt10() { return recallAt10; }
    public void setRecallAt10(double value) { recallAt10 = value; }
    public double getCitationCorrectness() { return citationCorrectness; }
    public void setCitationCorrectness(double value) { citationCorrectness = value; }
    public double getCorrectRefusalRate() { return correctRefusalRate; }
    public void setCorrectRefusalRate(double value) { correctRefusalRate = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
