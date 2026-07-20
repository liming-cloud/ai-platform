package com.aiplatform.knowledge.domain.port.ingest;

import com.aiplatform.knowledge.domain.DocumentSourceContent;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.port.ingest.model.ProcessedKnowledgeDocument;

/**
 * 文档解析和切块端口，业务上下文只依赖领域契约，具体 Reader 和 Splitter 由基础设施适配。
 */
public interface DocumentIngestProcessorPort {
    boolean supports(KnowledgeDocument document);

    ProcessedKnowledgeDocument process(KnowledgeDocument document, DocumentSourceContent sourceContent);
}
