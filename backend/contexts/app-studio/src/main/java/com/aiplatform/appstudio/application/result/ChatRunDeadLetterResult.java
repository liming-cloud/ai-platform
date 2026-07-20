package com.aiplatform.appstudio.application.result;

import com.aiplatform.appstudio.application.port.ChatRunRepository.DeadLetter;
import java.time.Instant;

/** Query projection intentionally excludes the retained finalization JSON. */
public record ChatRunDeadLetterResult(
        String runId,
        String sessionId,
        String applicationId,
        String originalActorId,
        int finalizationAttemptCount,
        int finalizationCycleAttemptCount,
        int manualReplayCount,
        String failureCode,
        String failureSummary,
        String failureTraceId,
        Instant updatedAt
) {
    public static ChatRunDeadLetterResult from(DeadLetter deadLetter) {
        return new ChatRunDeadLetterResult(
                deadLetter.runId(),
                deadLetter.sessionId(),
                deadLetter.applicationId(),
                deadLetter.actorId(),
                deadLetter.finalizationAttemptCount(),
                deadLetter.finalizationCycleAttemptCount(),
                deadLetter.manualReplayCount(),
                deadLetter.failureCode(),
                deadLetter.failureSummary(),
                deadLetter.failureTraceId(),
                deadLetter.updatedAt()
        );
    }
}
