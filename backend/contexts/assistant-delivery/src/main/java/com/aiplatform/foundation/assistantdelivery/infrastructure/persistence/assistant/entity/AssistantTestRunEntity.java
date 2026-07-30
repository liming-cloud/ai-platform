package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 草稿测试运行的最小持久化事实，不含题目、回答或证据正文。
 */
@TableName("assistant_delivery.assistant_test_run")
public final class AssistantTestRunEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id") private Long tenantId;
    @TableField("requested_by") private Long requestedBy;
    @TableField("assistant_version_id") private Long assistantVersionId;
    @TableField("knowledge_space_id") private Long knowledgeSpaceId;
    @TableField("index_version_id") private Long indexVersionId;
    @TableField("index_version_revision") private long indexVersionRevision;
    @TableField("chat_configuration_id") private Long chatConfigurationId;
    @TableField("chat_configuration_revision") private Long chatConfigurationRevision;
    @TableField("chat_catalog_entry_id") private Long chatCatalogEntryId;
    @TableField("chat_catalog_entry_revision") private Long chatCatalogEntryRevision;
    @TableField("chat_output_limit") private int chatOutputLimit;
    @TableField("embedding_configuration_id") private Long embeddingConfigurationId;
    @TableField("embedding_configuration_revision") private Long embeddingConfigurationRevision;
    @TableField("embedding_catalog_entry_id") private Long embeddingCatalogEntryId;
    @TableField("embedding_catalog_entry_revision") private Long embeddingCatalogEntryRevision;
    @TableField("embedding_dimension") private int embeddingDimension;
    @TableField("system_instruction") private String systemInstruction;
    @TableField("max_output_tokens") private int maxOutputTokens;
    @TableField("retrieval_limit") private int retrievalLimit;
    @TableField("minimum_score") private double minimumScore;
    @TableField("citation_policy") private String citationPolicy;
    @TableField("refusal_policy") private String refusalPolicy;
    @TableField("question_digest") private String questionDigest;
    @TableField("quota_period_start") private Instant quotaPeriodStart;
    @TableField("quota_period_end") private Instant quotaPeriodEnd;
    @TableField("daily_test_limit") private int dailyTestLimit;
    @TableField("daily_test_ordinal") private int dailyTestOrdinal;
    @TableField("concurrent_test_limit") private int concurrentTestLimit;
    @TableField("accepted_lease_until") private Instant acceptedLeaseUntil;
    @TableField("status") private String status;
    @TableField("refusal_reason") private String refusalReason;
    @TableField("input_tokens") private int inputTokens;
    @TableField("output_tokens") private int outputTokens;
    @TableField("total_tokens") private int totalTokens;
    @Version @TableField("version") private long version;
    @TableField("created_at") private Instant createdAt;
    @TableField("completed_at") private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long value) { requestedBy = value; }
    public Long getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(Long value) { assistantVersionId = value; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public Long getIndexVersionId() { return indexVersionId; }
    public void setIndexVersionId(Long value) { indexVersionId = value; }
    public long getIndexVersionRevision() { return indexVersionRevision; }
    public void setIndexVersionRevision(long value) { indexVersionRevision = value; }
    public Long getChatConfigurationId() { return chatConfigurationId; }
    public void setChatConfigurationId(Long value) { chatConfigurationId = value; }
    public Long getChatConfigurationRevision() { return chatConfigurationRevision; }
    public void setChatConfigurationRevision(Long value) { chatConfigurationRevision = value; }
    public Long getChatCatalogEntryId() { return chatCatalogEntryId; }
    public void setChatCatalogEntryId(Long value) { chatCatalogEntryId = value; }
    public Long getChatCatalogEntryRevision() { return chatCatalogEntryRevision; }
    public void setChatCatalogEntryRevision(Long value) { chatCatalogEntryRevision = value; }
    public int getChatOutputLimit() { return chatOutputLimit; }
    public void setChatOutputLimit(int value) { chatOutputLimit = value; }
    public Long getEmbeddingConfigurationId() { return embeddingConfigurationId; }
    public void setEmbeddingConfigurationId(Long value) { embeddingConfigurationId = value; }
    public Long getEmbeddingConfigurationRevision() { return embeddingConfigurationRevision; }
    public void setEmbeddingConfigurationRevision(Long value) { embeddingConfigurationRevision = value; }
    public Long getEmbeddingCatalogEntryId() { return embeddingCatalogEntryId; }
    public void setEmbeddingCatalogEntryId(Long value) { embeddingCatalogEntryId = value; }
    public Long getEmbeddingCatalogEntryRevision() { return embeddingCatalogEntryRevision; }
    public void setEmbeddingCatalogEntryRevision(Long value) { embeddingCatalogEntryRevision = value; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int value) { embeddingDimension = value; }
    public String getSystemInstruction() { return systemInstruction; }
    public void setSystemInstruction(String value) { systemInstruction = value; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    public int getRetrievalLimit() { return retrievalLimit; }
    public void setRetrievalLimit(int value) { retrievalLimit = value; }
    public double getMinimumScore() { return minimumScore; }
    public void setMinimumScore(double value) { minimumScore = value; }
    public String getCitationPolicy() { return citationPolicy; }
    public void setCitationPolicy(String value) { citationPolicy = value; }
    public String getRefusalPolicy() { return refusalPolicy; }
    public void setRefusalPolicy(String value) { refusalPolicy = value; }
    public String getQuestionDigest() { return questionDigest; }
    public void setQuestionDigest(String value) { questionDigest = value; }
    public Instant getQuotaPeriodStart() { return quotaPeriodStart; }
    public void setQuotaPeriodStart(Instant value) { quotaPeriodStart = value; }
    public Instant getQuotaPeriodEnd() { return quotaPeriodEnd; }
    public void setQuotaPeriodEnd(Instant value) { quotaPeriodEnd = value; }
    public int getDailyTestLimit() { return dailyTestLimit; }
    public void setDailyTestLimit(int value) { dailyTestLimit = value; }
    public int getDailyTestOrdinal() { return dailyTestOrdinal; }
    public void setDailyTestOrdinal(int value) { dailyTestOrdinal = value; }
    public int getConcurrentTestLimit() { return concurrentTestLimit; }
    public void setConcurrentTestLimit(int value) { concurrentTestLimit = value; }
    public Instant getAcceptedLeaseUntil() { return acceptedLeaseUntil; }
    public void setAcceptedLeaseUntil(Instant value) { acceptedLeaseUntil = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRefusalReason() { return refusalReason; }
    public void setRefusalReason(String value) { refusalReason = value; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int value) { inputTokens = value; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int value) { outputTokens = value; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int value) { totalTokens = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
