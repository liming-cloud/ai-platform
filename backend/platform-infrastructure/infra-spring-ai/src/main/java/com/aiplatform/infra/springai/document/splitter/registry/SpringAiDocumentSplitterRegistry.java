package com.aiplatform.infra.springai.document.splitter.registry;

import com.aiplatform.infra.springai.document.model.SpringAiDocumentSplitRequest;
import com.aiplatform.infra.springai.document.splitter.strategy.SpringAiDocumentSplitterStrategy;
import java.util.List;
import java.util.Objects;

/**
 * Spring AI 文档切分策略注册表，按 splitterName 选择具体切分实现。
 */
public class SpringAiDocumentSplitterRegistry {
    private final List<SpringAiDocumentSplitterStrategy> strategies;

    public SpringAiDocumentSplitterRegistry(List<SpringAiDocumentSplitterStrategy> strategies) {
        Objects.requireNonNull(strategies, "文档切分策略列表不能为空");
        this.strategies = List.copyOf(strategies);
    }

    public SpringAiDocumentSplitterStrategy select(SpringAiDocumentSplitRequest request) {
        Objects.requireNonNull(request, "文档切分请求不能为空");
        return strategies.stream()
                .filter(strategy -> strategy.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到支持该名称的 Spring AI 文档切分策略，splitterName=" + request.splitterName()));
    }
}
