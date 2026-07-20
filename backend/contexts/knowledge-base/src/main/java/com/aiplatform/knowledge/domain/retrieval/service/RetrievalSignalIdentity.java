package com.aiplatform.knowledge.domain.retrieval.service;

import com.aiplatform.knowledge.domain.retrieval.RetrievalCandidate;
import com.aiplatform.knowledge.domain.retrieval.RetrievalEvidenceIdentity;
import com.aiplatform.knowledge.domain.retrieval.RetrievalSignal;
import java.util.Objects;

/**
 * 融合排序内部的“证据 + 召回信号”去重键。
 */
record RetrievalSignalIdentity(
        RetrievalEvidenceIdentity evidenceIdentity,
        RetrievalSignal signal
) {
    RetrievalSignalIdentity {
        Objects.requireNonNull(evidenceIdentity, "证据身份不能为空");
        Objects.requireNonNull(signal, "召回信号不能为空");
    }

    static RetrievalSignalIdentity from(RetrievalCandidate candidate) {
        Objects.requireNonNull(candidate, "检索候选不能为空");
        return new RetrievalSignalIdentity(candidate.evidenceIdentity(), candidate.signal());
    }
}
