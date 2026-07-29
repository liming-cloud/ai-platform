package com.aiplatform.foundation.knowledgedata.application.datasource.port.out;

import com.aiplatform.foundation.knowledgedata.domain.datasource.revision.SourceRevision;

/**
 * 核验已冻结文件修订对应的平台受控对象。
 *
 * <p>实现只能使用修订内的平台对象事实访问私有存储；不得接受调用方提供的对象位置或凭据。</p>
 */
public interface FileSourceObjectProbe {

    /**
     * 核验对象存在且其摘要、长度和媒体类型均与来源修订一致。
     *
     * @throws RuntimeException 存储不可访问或元数据不一致时抛出，不得携带原始对象内容。
     */
    void verify(SourceRevision revision);
}
