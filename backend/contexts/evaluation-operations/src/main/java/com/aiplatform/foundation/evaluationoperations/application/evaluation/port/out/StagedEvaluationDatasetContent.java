package com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out;

import java.io.IOException;
import java.io.InputStream;

/**
 * 私有评测内容的短生命周期暂存结果；关闭后必须清理本地正文。
 */
public interface StagedEvaluationDatasetContent extends AutoCloseable {

    /**
     * 为对象存储写入重新打开已验证的私有字节流。
     */
    InputStream openStream() throws IOException;

    /**
     * 返回服务器对暂存字节计算的 SHA-256 摘要。
     */
    String contentDigest();

    /**
     * 返回服务端严格解析得到的条目总数。
     */
    int entryCount();

    /**
     * 清理暂存正文。
     */
    @Override
    void close() throws IOException;
}
