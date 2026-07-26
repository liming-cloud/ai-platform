package com.aiplatform.foundation.knowledgedata.application.port.out;

import java.io.IOException;
import java.io.InputStream;

/**
 * 对上传流实施大小、字符集和摘要治理的暂存端口。
 */
public interface TextContentStagingPort {

    StagedTextContent stage(InputStream input) throws IOException;
}
