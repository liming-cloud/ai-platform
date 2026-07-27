package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 查询引用持久化实体。
 */
@TableName("trusted_query.query_citation")
public class QueryCitationEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("query_run_id")
    private Long queryRunId;
    @TableField("sequence")
    private int sequence;
    @TableField("data_source_id")
    private Long dataSourceId;
    @TableField("content_version")
    private int contentVersion;
    @TableField("chunk_id")
    private String chunkId;
    @TableField("ordinal")
    private int ordinal;
    @TableField("quote")
    private String quote;
    @TableField("score")
    private double score;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getQueryRunId() { return queryRunId; }
    public void setQueryRunId(Long queryRunId) { this.queryRunId = queryRunId; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int value) { contentVersion = value; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
    public String getQuote() { return quote; }
    public void setQuote(String quote) { this.quote = quote; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
