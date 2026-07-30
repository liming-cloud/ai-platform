package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.PlatformObjectReference;
import java.io.InputStream;

/** 读取已冻结平台对象正文的内部端口，不暴露对象定位或连接凭据。 */
public interface PlatformObjectContentReaderPort {
    /** 返回对象正文流；调用方负责关闭。 */
    InputStream open(PlatformObjectReference object);
}
