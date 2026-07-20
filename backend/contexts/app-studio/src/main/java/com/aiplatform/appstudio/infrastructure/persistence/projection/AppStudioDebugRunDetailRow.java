package com.aiplatform.appstudio.infrastructure.persistence.projection;

import java.time.Instant;

/**
 * 调试运行详情主行投影。
 */
public class AppStudioDebugRunDetailRow {
    private Long assistantMessageId;
    private Long userMessageId;
    private Long applicationId;
    private Long sessionId;
    private String traceId;
    private Long modelInvocationLogId;
    private String invocationStatus;
    private String userMessageContent;
    private String assistantMessageContent;
    private String providerCode;
    private String modelCode;
    private String decisionSource;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer latencyMs;
    private String sourceContext;
    private String sourceResourceType;
    private String sourceResourceId;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;

    public Long getAssistantMessageId() { return assistantMessageId; }
    public void setAssistantMessageId(Long assistantMessageId) { this.assistantMessageId = assistantMessageId; }
    public Long getUserMessageId() { return userMessageId; }
    public void setUserMessageId(Long userMessageId) { this.userMessageId = userMessageId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getModelInvocationLogId() { return modelInvocationLogId; }
    public void setModelInvocationLogId(Long modelInvocationLogId) { this.modelInvocationLogId = modelInvocationLogId; }
    public String getInvocationStatus() { return invocationStatus; }
    public void setInvocationStatus(String invocationStatus) { this.invocationStatus = invocationStatus; }
    public String getUserMessageContent() { return userMessageContent; }
    public void setUserMessageContent(String userMessageContent) { this.userMessageContent = userMessageContent; }
    public String getAssistantMessageContent() { return assistantMessageContent; }
    public void setAssistantMessageContent(String assistantMessageContent) { this.assistantMessageContent = assistantMessageContent; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getDecisionSource() { return decisionSource; }
    public void setDecisionSource(String decisionSource) { this.decisionSource = decisionSource; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public String getSourceContext() { return sourceContext; }
    public void setSourceContext(String sourceContext) { this.sourceContext = sourceContext; }
    public String getSourceResourceType() { return sourceResourceType; }
    public void setSourceResourceType(String sourceResourceType) { this.sourceResourceType = sourceResourceType; }
    public String getSourceResourceId() { return sourceResourceId; }
    public void setSourceResourceId(String sourceResourceId) { this.sourceResourceId = sourceResourceId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
