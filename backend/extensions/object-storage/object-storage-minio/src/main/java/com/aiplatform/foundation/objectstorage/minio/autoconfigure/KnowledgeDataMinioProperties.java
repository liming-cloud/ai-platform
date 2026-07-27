package com.aiplatform.foundation.objectstorage.minio.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制面知识数据对象存储配置。
 */
@ConfigurationProperties("foundation.knowledge-data.object-storage")
public class KnowledgeDataMinioProperties extends MinioObjectStorageSettings {
}
