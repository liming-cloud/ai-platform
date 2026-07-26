package com.aiplatform.foundation.knowledgedata.api.extension;

/**
 * 向量存储扩展在当前事务中替换一个索引版本全部向量的端口。
 */
public interface IndexVectorWriter {

    void replace(IndexVectorBatch batch);
}
