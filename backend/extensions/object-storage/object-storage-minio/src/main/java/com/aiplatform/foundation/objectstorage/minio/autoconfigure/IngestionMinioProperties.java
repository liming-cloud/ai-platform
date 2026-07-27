package com.aiplatform.foundation.objectstorage.minio.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 摄取进程对象存储配置。
 */
@ConfigurationProperties("foundation.ingestion.object-storage")
public class IngestionMinioProperties extends MinioObjectStorageSettings {
}
