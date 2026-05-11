package org.fujay.ai.platform.ai;

public record ChatTestResult(String message, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
