package com.aiplatform.foundation.modelruntime.interfaces.web.platformsupply.request;

/** 发布输入只绑定预期连接修订，身份和验证事实由服务端读取。 */
public record PlatformProviderConnectionPublishRequest(Long expectedVersion) {
    public PlatformProviderConnectionPublishRequest {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("必须提供非负的预期连接修订");
        }
    }
}
