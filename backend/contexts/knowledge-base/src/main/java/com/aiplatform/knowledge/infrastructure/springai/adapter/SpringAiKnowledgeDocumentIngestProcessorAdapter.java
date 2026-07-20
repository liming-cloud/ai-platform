package com.aiplatform.knowledge.infrastructure.springai.adapter;

import com.aiplatform.infra.springai.document.SpringAiDocumentEtlGateway;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadProbe;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadRequest;
import com.aiplatform.knowledge.domain.DocumentSourceContent;
import com.aiplatform.knowledge.domain.KnowledgeDocument;
import com.aiplatform.knowledge.domain.port.ingest.DocumentIngestProcessorPort;
import com.aiplatform.knowledge.domain.port.ingest.model.KnowledgeDocumentChunkDraft;
import com.aiplatform.knowledge.domain.port.ingest.model.ProcessedKnowledgeDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spring AI 文档解析切分适配器，承接 knowledge-base 入库端口与通用 Spring AI ETL 网关的边界转换。
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpringAiKnowledgeDocumentIngestProcessorAdapter implements DocumentIngestProcessorPort {
    private static final String PROCESSOR_NAME = "spring-ai";

    private final SpringAiDocumentEtlGateway gateway;

    public SpringAiKnowledgeDocumentIngestProcessorAdapter() {
        this(new SpringAiDocumentEtlGateway());
    }

    SpringAiKnowledgeDocumentIngestProcessorAdapter(SpringAiDocumentEtlGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "Spring AI 文档 ETL 网关不能为空");
    }

    @Override
    public boolean supports(KnowledgeDocument document) {
        Objects.requireNonNull(document, "知识文档不能为空");
        return gateway.supportsRead(new SpringAiDocumentReadProbe(
                document.fileName(),
                document.contentType(),
                documentMetadata(document, document.sourceUri())
        ));
    }

    @Override
    public ProcessedKnowledgeDocument process(KnowledgeDocument document, DocumentSourceContent sourceContent) {
        Objects.requireNonNull(document, "知识文档不能为空");
        Objects.requireNonNull(sourceContent, "文档源内容不能为空");
        List<Document> documents = requireTextDocuments(gateway.read(new SpringAiDocumentReadRequest(
                document.fileName(),
                document.contentType(),
                sourceContent.content(),
                documentMetadata(document, sourceContent.sourceUri())
        )), "Spring AI 文档读取结果");
        List<Document> splitDocuments = Objects.requireNonNull(
                gateway.split(documents),
                "Spring AI 文档切分结果不能为空"
        );
        if (splitDocuments.isEmpty()) {
            splitDocuments = documents;
        } else {
            splitDocuments = requireTextDocuments(splitDocuments, "Spring AI 文档切分结果");
        }
        return new ProcessedKnowledgeDocument(
                PROCESSOR_NAME,
                gateway.defaultChunkStrategyVersion(),
                toDrafts(sourceText(documents), splitDocuments)
        );
    }

    private static String sourceText(List<Document> documents) {
        return documents.stream()
                .map(document -> requireDocumentText(document, "Spring AI 文档读取结果"))
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
    }

    private static Map<String, Object> documentMetadata(KnowledgeDocument document, String sourceUri) {
        return Map.of(
                "tenantId", document.tenantId().value(),
                "knowledgeBaseId", document.knowledgeBaseId(),
                "documentId", document.documentId(),
                "sourceUri", sourceUri
        );
    }

    private static List<KnowledgeDocumentChunkDraft> toDrafts(String sourceText, List<Document> documents) {
        ArrayList<KnowledgeDocumentChunkDraft> drafts = new ArrayList<>(documents.size());
        int searchFrom = 0;
        for (Document document : documents) {
            String content = requireDocumentText(document, "Spring AI 文档切分结果").strip();
            if (content.isBlank()) {
                continue;
            }
            int positionStart = sourceText.indexOf(content, searchFrom);
            if (positionStart < 0) {
                positionStart = Math.min(searchFrom, sourceText.length());
            }
            int positionEnd = positionStart + content.length();
            searchFrom = Math.min(positionEnd, sourceText.length());
            drafts.add(new KnowledgeDocumentChunkDraft(
                    content,
                    content.codePointCount(0, content.length()),
                    null,
                    positionStart,
                    positionEnd
            ));
        }
        return List.copyOf(drafts);
    }

    private static List<Document> requireTextDocuments(List<Document> documents, String fieldName) {
        Objects.requireNonNull(documents, fieldName + "不能为空");
        for (Document document : documents) {
            requireDocumentText(document, fieldName);
        }
        return documents;
    }

    private static String requireDocumentText(Document document, String fieldName) {
        Objects.requireNonNull(document, fieldName + "不能包含空文档");
        String text = document.getText();
        if (text == null) {
            throw new IllegalStateException(fieldName + "缺失文本内容");
        }
        return text;
    }
}
