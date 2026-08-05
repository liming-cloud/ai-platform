package com.aiplatform.foundation.assistantdelivery.application.evaluation.facade;

import java.util.regex.Pattern;

/** 评测运行可冻结的助手版本与受控路由摘要，不包含模型配置细节或正文。 */
public record EvaluationAssistantTarget(
        long assistantVersionId,
        long assistantVersionRevision,
        String modelRouteFingerprint) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public EvaluationAssistantTarget {
        if (assistantVersionId <= 0 || assistantVersionRevision < 0
                || modelRouteFingerprint == null || !SHA256.matcher(modelRouteFingerprint).matches()) {
            throw new IllegalArgumentException("评测助手目标不完整");
        }
    }
}
