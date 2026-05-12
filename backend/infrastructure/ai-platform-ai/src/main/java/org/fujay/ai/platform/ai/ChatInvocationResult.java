package org.fujay.ai.platform.ai;

public record ChatInvocationResult(
        String output,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
