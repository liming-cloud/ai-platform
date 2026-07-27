package com.aiplatform.foundation.knowledgedata.application.port.out.content;

import java.io.IOException;
import java.io.InputStream;

/**
 * 可重复打开且关闭后自动清理的受限文本暂存结果。
 */
public interface StagedTextContent extends AutoCloseable {

    long byteSize();

    String sha256();

    InputStream openStream() throws IOException;

    @Override
    void close();
}
