package com.aiplatform.foundation.assistantdelivery.application.evaluation.facade;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Optional;

/**
 * ADR-0055 唯一允许逐条交接私有评测输入的受限内存门面。
 */
public interface EvaluationAssistantExecution {

    /** 查找当前主体可冻结的同租户草稿评测目标。 */
    Optional<EvaluationAssistantTarget> freeze(TrustedRequestContext context, long assistantVersionId);

    /**
     * 在调用期内执行单条私有评测输入，且只返回无正文事实。
     *
     * @throws com.aiplatform.foundation.assistantdelivery.application.assistant.exception.AssistantPermissionDeniedException
     *         当前主体没有 `assistant:write` 权限
     */
    EvaluationAssistantExecutionResult execute(
            TrustedRequestContext context,
            EvaluationAssistantTarget target,
            String input);
}
