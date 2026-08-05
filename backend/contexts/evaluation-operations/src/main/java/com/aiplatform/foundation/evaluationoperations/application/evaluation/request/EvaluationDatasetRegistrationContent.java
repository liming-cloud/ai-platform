package com.aiplatform.foundation.evaluationoperations.application.evaluation.request;

import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 单次数据集登记请求中的私有内容流及其已核验摘要；该对象不得持久化或投影给接口层。
 */
public record EvaluationDatasetRegistrationContent(
        InputStream content,
        String contentDigest,
        int entryCount,
        String retentionPolicyId) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public EvaluationDatasetRegistrationContent {
        content = Objects.requireNonNull(content, "评测私有内容不能为空");
        if (contentDigest == null || !SHA256.matcher(contentDigest).matches()) {
            throw new IllegalArgumentException("评测私有内容摘要格式不正确");
        }
        if (entryCount <= 0 || retentionPolicyId == null || retentionPolicyId.isBlank()
                || !retentionPolicyId.equals(retentionPolicyId.trim())) {
            throw new IllegalArgumentException("评测数据集登记参数不正确");
        }
    }
}
