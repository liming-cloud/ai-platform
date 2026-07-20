package com.aiplatform.infra.objectstorage.gateway;

import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageContent;
import com.aiplatform.infra.objectstorage.gateway.model.ObjectStorageObject;
import com.aiplatform.infra.objectstorage.gateway.model.StoredObject;

/**
 * 对象存储网关，隔离业务上下文与 S3/MinIO 协议实现。
 */
public interface ObjectStorageGateway {
    StoredObject put(ObjectStorageObject object);

    ObjectStorageContent get(String bucket, String objectKey);
}
