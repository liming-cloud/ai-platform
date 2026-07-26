package com.aiplatform.foundation.trustedquery.adapter.out.persistence;

/**
 * 查询状态、令牌和当前配额占用的数据库汇总行。
 */
public final class QueryUsageRow {

    private long acceptedQueries;
    private long answeredQueries;
    private long refusedQueries;
    private long failedQueries;
    private long runningQueries;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private long dailyUsed;
    private long concurrentUsed;

    public long getAcceptedQueries() { return acceptedQueries; }
    public void setAcceptedQueries(long value) { acceptedQueries = value; }
    public long getAnsweredQueries() { return answeredQueries; }
    public void setAnsweredQueries(long value) { answeredQueries = value; }
    public long getRefusedQueries() { return refusedQueries; }
    public void setRefusedQueries(long value) { refusedQueries = value; }
    public long getFailedQueries() { return failedQueries; }
    public void setFailedQueries(long value) { failedQueries = value; }
    public long getRunningQueries() { return runningQueries; }
    public void setRunningQueries(long value) { runningQueries = value; }
    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long value) { inputTokens = value; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long value) { outputTokens = value; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long value) { totalTokens = value; }
    public long getDailyUsed() { return dailyUsed; }
    public void setDailyUsed(long value) { dailyUsed = value; }
    public long getConcurrentUsed() { return concurrentUsed; }
    public void setConcurrentUsed(long value) { concurrentUsed = value; }
}
