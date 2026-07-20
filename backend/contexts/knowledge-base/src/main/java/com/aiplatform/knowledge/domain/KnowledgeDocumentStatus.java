package com.aiplatform.knowledge.domain;

public enum KnowledgeDocumentStatus {
    UPLOADED,
    PARSING,
    CHUNKED,
    VECTORIZING,
    AVAILABLE,
    FAILED,
    CANCELLED,
    EXPIRED
}
