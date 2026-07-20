package com.aiplatform.appstudio.domain.publish;

import java.util.List;
import java.util.Objects;

public record AppPublishPreflightDecision(
        boolean passed,
        List<PublishPreflightIssue> blockers,
        List<PublishPreflightIssue> warnings
) {
    public AppPublishPreflightDecision {
        blockers = copy(blockers, "阻断项");
        warnings = copy(warnings, "警告项");
        if (passed != blockers.isEmpty()) {
            throw new IllegalArgumentException("发布预检通过状态与阻断项不一致");
        }
    }

    private static List<PublishPreflightIssue> copy(List<PublishPreflightIssue> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不能为空");
        return values.stream().map(value -> Objects.requireNonNull(value, fieldName + "不能为空")).toList();
    }
}
