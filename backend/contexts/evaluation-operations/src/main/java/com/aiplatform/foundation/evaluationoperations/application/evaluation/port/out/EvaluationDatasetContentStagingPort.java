package com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out;

import java.io.IOException;
import java.io.InputStream;

/**
 * 将一次性私有上传流暂存并验证为可重复读取的受控内容。
 */
public interface EvaluationDatasetContentStagingPort {

    /**
     * 解析条目语义并返回服务端计算的摘要和计数。
     *
     * @throws IOException 当上传流或安全暂存失败时拒绝登记。
     */
    StagedEvaluationDatasetContent stage(InputStream input) throws IOException;
}
