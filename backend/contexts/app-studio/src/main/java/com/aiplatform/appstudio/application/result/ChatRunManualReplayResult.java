package com.aiplatform.appstudio.application.result;

/** Safe manual replay acknowledgement; finalization facts and failure exception text are never exposed. */
public record ChatRunManualReplayResult(String runId, String replayRequestId, String status) {
}
