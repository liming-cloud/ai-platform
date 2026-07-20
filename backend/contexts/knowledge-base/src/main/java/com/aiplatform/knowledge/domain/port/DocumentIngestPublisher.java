package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.DocumentIngestJob;

public interface DocumentIngestPublisher {
    void publish(DocumentIngestJob job);
}
