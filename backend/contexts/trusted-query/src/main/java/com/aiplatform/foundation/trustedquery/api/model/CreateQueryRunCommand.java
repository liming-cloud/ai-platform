package com.aiplatform.foundation.trustedquery.api.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 在可信会话中执行已发布助手版本的请求。
 */
public record CreateQueryRunCommand(
        TrustedRequestContext context,
        String idempotencyKey,
        String assistantVersionId,
        String question) {

    public CreateQueryRunCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(assistantVersionId, "助手版本标识不能为空");
        Objects.requireNonNull(question, "问题不能为空");
        if (idempotencyKey.isBlank()
                || !idempotencyKey.equals(idempotencyKey.trim())
                || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
        if (question.isBlank()
                || !question.equals(question.trim())
                || question.codePointCount(0, question.length()) > 2000) {
            throw new IllegalArgumentException("问题格式不正确");
        }
    }
}
