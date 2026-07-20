package com.aiplatform.modelhub.application;

import com.aiplatform.modelhub.domain.ModelConfig;
import com.aiplatform.modelhub.domain.ModelConnectivityStatus;
import com.aiplatform.modelhub.domain.ModelProvider;
import com.aiplatform.modelhub.domain.ModelRuntimeConnectivity;
import com.aiplatform.modelhub.domain.port.ModelRuntimeOptionsPort;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 在独立虚拟线程中执行运行时探测，并以平台上限约束等待时间。
 */
@Component
public final class BoundedModelConnectivityProbe implements ModelConnectivityProbe {
    static final Duration MAX_TIMEOUT = Duration.ofSeconds(60);

    private final ModelRuntimeOptionsPort runtimeOptions;
    private final Duration timeout;
    private final ExecutorService executor;

    @Autowired
    public BoundedModelConnectivityProbe(
            ModelRuntimeOptionsPort runtimeOptions,
            @Value("${aip.model-hub.connectivity-probe-timeout:10s}") Duration timeout
    ) {
        this(runtimeOptions, timeout, Executors.newVirtualThreadPerTaskExecutor());
    }

    BoundedModelConnectivityProbe(
            ModelRuntimeOptionsPort runtimeOptions,
            Duration timeout,
            ExecutorService executor
    ) {
        this.runtimeOptions = Objects.requireNonNull(runtimeOptions, "模型运行时 options 端口不能为空");
        this.timeout = requireTimeout(timeout);
        this.executor = Objects.requireNonNull(executor, "模型连通性探测执行器不能为空");
    }

    @Override
    public ModelRuntimeConnectivity test(ModelProvider provider, ModelConfig config) {
        Objects.requireNonNull(provider, "模型供应商不能为空");
        Objects.requireNonNull(config, "模型配置不能为空");
        Future<ModelRuntimeConnectivity> future = executor.submit(
                () -> runtimeOptions.testConnectivity(provider, config)
        );
        try {
            return Objects.requireNonNull(
                    future.get(timeout.toMillis(), TimeUnit.MILLISECONDS),
                    "模型运行时探测结果不能为空"
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            return failed("模型运行时探测超时");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return failed("模型运行时探测已中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("模型运行时探测执行失败", cause);
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static Duration requireTimeout(Duration value) {
        Objects.requireNonNull(value, "模型连通性探测超时不能为空");
        if (value.isZero() || value.isNegative() || value.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("模型连通性探测超时必须大于 0 且不超过 60 秒");
        }
        return value;
    }

    private static ModelRuntimeConnectivity failed(String message) {
        return new ModelRuntimeConnectivity(false, ModelConnectivityStatus.FAILED, message);
    }
}
