package com.aiplatform.contracts.capability.knowledge;

/**
 * 知识检索引用契约，只暴露回答所需的授权后来源片段，不泄漏 knowledge-base 内部模型。
 */
public record KnowledgeRetrievalCitation(
        String knowledgeBaseId,
        String documentId,
        String chunkId,
        String documentName,
        String fragment,
        String location,
        double score
) {
    public KnowledgeRetrievalCitation {
        knowledgeBaseId = KnowledgeRetrievalContracts.requireText(knowledgeBaseId, "知识库 ID");
        documentId = KnowledgeRetrievalContracts.requireText(documentId, "文档 ID");
        chunkId = KnowledgeRetrievalContracts.requireText(chunkId, "切片 ID");
        documentName = KnowledgeRetrievalContracts.requireText(documentName, "文档名称");
        fragment = KnowledgeRetrievalContracts.requireText(fragment, "引用片段");
        location = KnowledgeRetrievalContracts.requireText(location, "引用位置");
        score = KnowledgeRetrievalContracts.requireScore(score, "引用分数");
    }
}
