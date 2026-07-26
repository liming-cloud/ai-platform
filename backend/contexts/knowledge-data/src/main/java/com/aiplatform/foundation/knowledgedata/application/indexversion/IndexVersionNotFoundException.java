package com.aiplatform.foundation.knowledgedata.application.indexversion;

/**
 * 可信租户范围内不存在目标索引版本。
 */
public final class IndexVersionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IndexVersionNotFoundException() {
        super("索引版本不存在");
    }
}
