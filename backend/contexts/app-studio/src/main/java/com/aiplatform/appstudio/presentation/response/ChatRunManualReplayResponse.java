package com.aiplatform.appstudio.presentation.response;

import com.aiplatform.appstudio.application.result.ChatRunManualReplayResult;

public record ChatRunManualReplayResponse(String runId, String replayRequestId, String status) {
    public static ChatRunManualReplayResponse from(ChatRunManualReplayResult result) {
        return new ChatRunManualReplayResponse(result.runId(), result.replayRequestId(), result.status());
    }
}
