package com.aiplatform.foundation.trustedquery.application.diagnostic.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.trustedquery.domain.query.model.QueryRunStatus;
import org.junit.jupiter.api.Test;

class QueryDiagnosticFactTest {

    @Test
    void 已收集候选的回答应保存精确数量和已核验引用() {
        assertDoesNotThrow(() -> new QueryDiagnosticFact(
                CandidateCollection.COLLECTED, 11, RetrievalAuthorization.AUTHORIZED,
                3, 5, CitationValidation.VALIDATED));
    }

    @Test
    void 非回答终态不得伪造已核验引用() {
        assertThrows(IllegalArgumentException.class, () -> QueryDiagnosticFact.forTerminal(
                QueryRunStatus.REFUSED, CandidateCollection.COLLECTED, 0,
                3, 5, CitationValidation.VALIDATED));
    }

    @Test
    void 未收集候选的失败必须没有候选数量() {
        assertDoesNotThrow(() -> QueryDiagnosticFact.forTerminal(
                QueryRunStatus.FAILED, CandidateCollection.NOT_COLLECTED, null,
                3, 5, CitationValidation.NOT_APPLICABLE));
        assertThrows(IllegalArgumentException.class, () -> new QueryDiagnosticFact(
                CandidateCollection.NOT_COLLECTED, 0, RetrievalAuthorization.AUTHORIZED,
                3, 5, CitationValidation.NOT_APPLICABLE));
        assertThrows(IllegalArgumentException.class, () -> new QueryDiagnosticFact(
                CandidateCollection.COLLECTED, null, RetrievalAuthorization.AUTHORIZED,
                3, 5, CitationValidation.NOT_APPLICABLE));
    }
}
