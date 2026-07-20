package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ChatRunDeadLetterResult;
import java.time.Instant;

public record ChatRunDeadLetterResponse(
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
    public static ChatRunDeadLetterResponse from(ChatRunDeadLetterResult result) {
        return new ChatRunDeadLetterResponse(
                result.runId(), result.sessionId(), result.applicationId(), result.originalActorId(),
                result.finalizationAttemptCount(), result.finalizationCycleAttemptCount(),
                result.manualReplayCount(), result.failureCode(), result.failureSummary(),
                result.failureTraceId(), result.updatedAt()
        );
    }
}
