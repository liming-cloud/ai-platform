package com.aiplatform.foundation.knowledgedata.application.indexversion;

/**
 * 知识空间当前没有可冻结的已发布内容。
 */
public final class NoPublishedSourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoPublishedSourceException() {
        super("知识空间没有已发布内容");
    }
}
