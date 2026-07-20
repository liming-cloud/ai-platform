package com.aiplatform.knowledge.domain.port;

import com.aiplatform.knowledge.domain.DocumentSourceContent;

public interface DocumentSourceReaderPort {
    DocumentSourceContent read(String sourceUri);
}
