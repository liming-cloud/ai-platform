package com.aiplatform.foundation.evaluationoperations.application.evaluation.request;

import java.io.InputStream;
import java.util.Objects;

/**
 * 登记私有评测数据集的一次性上传请求；摘要和条目数必须由服务端生成。
 */
public record EvaluationDatasetRegistrationRequest(
        InputStream content,
        String retentionPolicyId) {

    public EvaluationDatasetRegistrationRequest {
        content = Objects.requireNonNull(content, "评测私有内容不能为空");
        if (retentionPolicyId == null || retentionPolicyId.isBlank()
                || !retentionPolicyId.equals(retentionPolicyId.trim())) {
            throw new IllegalArgumentException("评测数据集保留策略标识不正确");
        }
    }
}
