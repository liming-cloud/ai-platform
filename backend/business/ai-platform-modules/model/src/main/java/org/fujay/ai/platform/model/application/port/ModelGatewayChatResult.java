package org.fujay.ai.platform.model.application.port;

public record ModelGatewayChatResult(
        String output,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
