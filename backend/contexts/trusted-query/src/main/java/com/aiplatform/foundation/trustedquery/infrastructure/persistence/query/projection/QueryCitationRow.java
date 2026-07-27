package com.aiplatform.foundation.trustedquery.infrastructure.persistence.query.projection;

/**
 * 查询引用数据库行。
 */
public final class QueryCitationRow {

    private int sequence;
    private String dataSourceId;
    private int contentVersion;
    private String chunkId;
    private int ordinal;
    private String quote;
    private double score;

    public int getSequence() { return sequence; }
    public void setSequence(int value) { sequence = value; }
    public String getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(String value) { dataSourceId = value; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int value) { contentVersion = value; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String value) { chunkId = value; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int value) { ordinal = value; }
    public String getQuote() { return quote; }
    public void setQuote(String value) { quote = value; }
    public double getScore() { return score; }
    public void setScore(double value) { score = value; }
}
