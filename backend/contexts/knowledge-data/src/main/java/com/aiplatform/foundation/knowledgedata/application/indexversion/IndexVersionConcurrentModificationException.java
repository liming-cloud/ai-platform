package com.aiplatform.foundation.knowledgedata.application.indexversion;

/**
 * 索引版本状态或乐观锁修订已经改变。
 */
public final class IndexVersionConcurrentModificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IndexVersionConcurrentModificationException() {
        super("索引版本已被其他请求修改");
    }
}
