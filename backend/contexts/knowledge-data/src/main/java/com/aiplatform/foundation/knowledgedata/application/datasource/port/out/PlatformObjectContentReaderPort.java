package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;
import java.io.InputStream;

/** 读取已冻结平台对象正文的内部端口，不暴露对象定位或连接凭据。 */
public interface PlatformObjectContentReaderPort {
    /**
     * 返回冻结来源修订中指定对象的正文流；调用方负责关闭。
     *
     * @throws IllegalArgumentException 来源不是对象存储修订或对象不属于冻结集合时抛出。
     */
    InputStream open(SourceRevision revision, PlatformObjectReference object);
}
