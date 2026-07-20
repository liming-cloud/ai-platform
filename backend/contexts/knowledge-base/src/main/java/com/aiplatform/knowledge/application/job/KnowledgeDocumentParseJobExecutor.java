package com.aiplatform.knowledge.application.job;

import com.aiplatform.knowledge.application.KnowledgeDocumentIngestApplicationService;
import com.aiplatform.knowledge.application.ingest.model.PreparedKnowledgeDocumentParse;
import com.aiplatform.knowledge.domain.ClaimedDocumentIngestJob;
import com.aiplatform.knowledge.domain.DocumentIngestJob;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 解析执行器只编排外部解析和短事务 finalizer，自身不声明事务。
 */
@Service
public class KnowledgeDocumentParseJobExecutor {
    private final KnowledgeDocumentIngestApplicationService ingest;
    private final KnowledgeDocumentJobTransactionService transactions;

    public KnowledgeDocumentParseJobExecutor(
            KnowledgeDocumentIngestApplicationService ingest,
            KnowledgeDocumentJobTransactionService transactions
    ) {
        this.ingest = Objects.requireNonNull(ingest, "文档解析服务不能为空");
        this.transactions = Objects.requireNonNull(transactions, "文档作业短事务服务不能为空");
    }

    public void execute(ClaimedDocumentIngestJob claimed) {
        Objects.requireNonNull(claimed, "已认领解析任务不能为空");
        if (!DocumentIngestJob.JOB_TYPE_DOCUMENT_PARSE.equals(claimed.job().jobType())) {
            throw KnowledgeDocumentJobFailure.terminal(
                    "AIP-KB-JOB-TYPE", "解析执行器收到非解析任务"
            );
        }
        PreparedKnowledgeDocumentParse prepared = ingest.prepareParse(claimed);
        transactions.completeParse(claimed, prepared);
    }
}
