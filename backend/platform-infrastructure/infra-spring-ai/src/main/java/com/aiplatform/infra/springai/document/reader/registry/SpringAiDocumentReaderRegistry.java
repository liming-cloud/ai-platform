package com.aiplatform.infra.springai.document.reader.registry;

import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadDescriptor;
import com.aiplatform.infra.springai.document.model.SpringAiDocumentReadRequest;
import com.aiplatform.infra.springai.document.reader.strategy.SpringAiDocumentReaderStrategy;
import java.util.List;
import java.util.Objects;

/**
 * Spring AI 文档读取策略注册表，按 contentType 选择具体 Reader 适配器。
 */
public class SpringAiDocumentReaderRegistry {
    private final List<SpringAiDocumentReaderStrategy> strategies;

    public SpringAiDocumentReaderRegistry(List<SpringAiDocumentReaderStrategy> strategies) {
        Objects.requireNonNull(strategies, "文档读取策略列表不能为空");
        this.strategies = List.copyOf(strategies);
    }

    public SpringAiDocumentReaderStrategy select(SpringAiDocumentReadRequest request) {
        Objects.requireNonNull(request, "文档读取请求不能为空");
        return strategies.stream()
                .filter(strategy -> strategy.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到支持该内容类型的 Spring AI 文档读取策略，contentType=" + request.contentType()));
    }

    public boolean supports(SpringAiDocumentReadDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "文档读取支持性探测请求不能为空");
        return strategies.stream().anyMatch(strategy -> strategy.supports(descriptor));
    }
}
