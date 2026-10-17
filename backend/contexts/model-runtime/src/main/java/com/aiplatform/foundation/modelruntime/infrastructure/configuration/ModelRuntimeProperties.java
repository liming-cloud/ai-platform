package com.aiplatform.foundation.modelruntime.infrastructure.configuration;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型运行时缓存、调用和出站策略配置。
 */
@ConfigurationProperties("foundation.model-runtime")
public class ModelRuntimeProperties {

    private int cacheMaximumSize = 64;
    private Duration cacheIdleTimeout = Duration.ofMinutes(30);
    private Duration callTimeout = Duration.ofSeconds(30);
    private List<URI> allowedEndpoints = new ArrayList<>();
    private boolean platformConnectionWritesEnabled = true;

    /** 仅控制 S1 连接写命令，不影响读取、推理或租户配置。 */
    public boolean isPlatformConnectionWritesEnabled() { return platformConnectionWritesEnabled; }

    /** 启动时绑定；应用服务接收不可变快照，不支持浏览器或热配置覆盖。 */
    public void setPlatformConnectionWritesEnabled(boolean enabled) { platformConnectionWritesEnabled = enabled; }

    public int getCacheMaximumSize() {
        return cacheMaximumSize;
    }

    public void setCacheMaximumSize(int cacheMaximumSize) {
        if (cacheMaximumSize < 1) {
            throw new IllegalArgumentException("模型缓存上限必须大于零");
        }
        this.cacheMaximumSize = cacheMaximumSize;
    }

    public Duration getCacheIdleTimeout() {
        return cacheIdleTimeout;
    }

    public void setCacheIdleTimeout(Duration cacheIdleTimeout) {
        this.cacheIdleTimeout = requirePositive(cacheIdleTimeout, "模型缓存空闲时间");
    }

    public Duration getCallTimeout() {
        return callTimeout;
    }

    public void setCallTimeout(Duration callTimeout) {
        this.callTimeout = requirePositive(callTimeout, "模型调用超时");
    }

    public List<URI> getAllowedEndpoints() {
        return List.copyOf(allowedEndpoints);
    }

    public void setAllowedEndpoints(List<URI> allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints == null
                ? new ArrayList<>()
                : new ArrayList<>(allowedEndpoints);
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + "必须大于零");
        }
        return value;
    }
}
