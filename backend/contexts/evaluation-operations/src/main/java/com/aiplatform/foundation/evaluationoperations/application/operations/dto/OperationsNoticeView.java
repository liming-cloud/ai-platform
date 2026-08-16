package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

/** 可直接理解并下钻处理的运营注意事项。 */
public record OperationsNoticeView(
        String severity,
        String title,
        String description,
        String targetRoute) {
}
