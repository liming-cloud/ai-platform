package com.aiplatform.foundation.trustedquery.api.model;

import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;
import java.util.Objects;

/**
 * 受信上下文中的自然语言候选检索请求。
 */
public record CandidateSearchQuery(
        TrustedRequestContext context,
        String knowledgeSpaceId,
        String text,
        int limit) {

    public CandidateSearchQuery {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(knowledgeSpaceId, "知识空间标识不能为空");
        Objects.requireNonNull(text, "查询文本不能为空");
        if (text.isBlank() || text.codePointCount(0, text.length()) > 2000) {
            throw new IllegalArgumentException("查询文本格式不正确");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("候选数量必须在 1 到 20 之间");
        }
    }
}
