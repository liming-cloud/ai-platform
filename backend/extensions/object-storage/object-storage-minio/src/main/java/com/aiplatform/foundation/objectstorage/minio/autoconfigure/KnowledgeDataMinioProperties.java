package com.aiplatform.foundation.objectstorage.minio.autoconfigure;

import com.aiplatform.foundation.objectstorage.minio.PlatformObjectLocation;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 控制面知识数据对象存储配置。
 */
@ConfigurationProperties("foundation.knowledge-data.object-storage")
public class KnowledgeDataMinioProperties extends MinioObjectStorageSettings {

    private List<PlatformObjectLocation> platformObjectLocations = List.of();

    /**
     * 返回平台不透明对象到 MinIO 内部对象键的部署级映射。
     *
     * <p>该配置只由 MinIO 读取适配器消费，不会向控制面、领域模型或接口层传播。</p>
     */
    public List<PlatformObjectLocation> getPlatformObjectLocations() {
        return platformObjectLocations;
    }

    public void setPlatformObjectLocations(List<PlatformObjectLocation> value) {
        platformObjectLocations = List.copyOf(value == null ? List.of() : value);
    }
}
