package com.aiplatform.infra.springai.model.registry;

import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import com.aiplatform.infra.springai.model.strategy.SpringAiModelOptionsStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

/**
 * Spring AI 模型 options 策略注册表，按请求中的模型类型选择可扩展策略。
 */
@Component
public class SpringAiModelOptionsRegistry {
    private final List<SpringAiModelOptionsStrategy> strategies;

    public SpringAiModelOptionsRegistry(List<SpringAiModelOptionsStrategy> strategies) {
        Objects.requireNonNull(strategies, "模型 options 策略列表不能为空");
        List<SpringAiModelOptionsStrategy> orderedStrategies = new ArrayList<>(strategies);
        AnnotationAwareOrderComparator.sort(orderedStrategies);
        this.strategies = List.copyOf(orderedStrategies);
    }

    public SpringAiModelOptionsStrategy select(SpringAiModelRuntimeRequest request) {
        Objects.requireNonNull(request, "模型运行时请求不能为空");
        return strategies.stream()
                .filter(strategy -> strategy.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到支持该模型类型的 Spring AI options 策略，modelType=" + request.modelType()));
    }
}
