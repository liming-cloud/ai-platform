package com.aiplatform.knowledge.presentation.retrieval.response;

import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import com.aiplatform.knowledge.application.retrieval.result.RetrievedKnowledgeChunk;
import java.util.Objects;

/**
 * 授权后的知识切片引用响应，只包含允许当前用户查看的来源和片段字段。
 */
public record RetrievedKnowledgeChunkResponse(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String content,
        double score,
        RetrievalSignal signal,
        String sourceUri,
        String titlePath,
        Integer paragraphNo,
        String tableCellRange,
        boolean authoritative,
        String indexVersion
) {
    public RetrievedKnowledgeChunkResponse {
        knowledgeBaseId = requireText(knowledgeBaseId, "知识库ID");
        documentId = requireText(documentId, "文档ID");
        chunkId = requireText(chunkId, "切片ID");
        content = requireText(content, "切片内容");
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("召回分数必须在 0 到 1 之间");
        }
        Objects.requireNonNull(signal, "召回信号不能为空");
        sourceUri = requireText(sourceUri, "来源地址");
        titlePath = requireText(titlePath, "标题路径");
        if (paragraphNo != null && paragraphNo < 1) {
            throw new IllegalArgumentException("段落编号必须为正数");
        }
        tableCellRange = tableCellRange == null ? null : requireText(tableCellRange, "表格单元格范围");
        indexVersion = requireText(indexVersion, "索引版本");
    }

    static RetrievedKnowledgeChunkResponse from(RetrievedKnowledgeChunk chunk) {
        Objects.requireNonNull(chunk, "检索切片不能为空");
        return new RetrievedKnowledgeChunkResponse(
                chunk.knowledgeBaseId(),
                chunk.documentId(),
                chunk.chunkId(),
                chunk.content(),
                chunk.score(),
                chunk.signal(),
                chunk.sourceUri(),
                chunk.titlePath(),
                chunk.paragraphNo(),
                chunk.tableCellRange(),
                chunk.authoritative(),
                chunk.indexVersion()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
