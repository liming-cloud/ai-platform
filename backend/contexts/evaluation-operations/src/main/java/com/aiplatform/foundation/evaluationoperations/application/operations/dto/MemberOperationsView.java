package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

/** 租户管理员与普通用户的启停分布。 */
public record MemberOperationsView(
        ResourceStatusView administrators,
        ResourceStatusView users) {
}
