package com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetEntry;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * 私有 JSON Lines 的逐条读取端口；消费者不得在回调结束后保留条目正文。
 */
public interface EvaluationDatasetEntryReader {

    int read(InputStream content, Consumer<EvaluationDatasetEntry> consumer) throws IOException;
}
