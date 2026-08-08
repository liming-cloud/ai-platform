package com.aiplatform.foundation.evaluationoperations.interfaces.web.release.controller;

import com.aiplatform.foundation.evaluationoperations.domain.release.model.ReleaseDecision;
import java.time.Instant;

/**
 * 发布决定的无正文投影；不暴露评测输入、回答、证据或模型路由信息。
 */
public record ReleaseDecisionView(
        String id,
        String evaluationRunId,
        String assistantVersionId,
        long assistantVersionRevision,
        String policyVersion,
        String status,
        String reasonCode,
        long version,
        Instant decidedAt) {

    static ReleaseDecisionView from(ReleaseDecision decision) {
        return new ReleaseDecisionView(
                Long.toString(decision.id().value()),
                Long.toString(decision.evaluationRunId().value()),
                Long.toString(decision.target().assistantVersionId()),
                decision.target().assistantVersionRevision(),
                decision.policyVersion(),
                decision.status().name(),
                decision.reasonCode().name(),
                decision.version(),
                decision.decidedAt());
    }
}
