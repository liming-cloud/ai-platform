package com.aiplatform.infra.springai.document.splitter.strategy;

import com.aiplatform.infra.springai.document.model.SpringAiDocumentSplitRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

/**
 * 默认 token 切分策略，封装 Spring AI TokenTextSplitter 以保留后续算法替换空间。
 */
public class SpringAiTokenTextSplitterStrategy implements SpringAiDocumentSplitterStrategy {
    public static final String NAME = "token";

    private final TokenTextSplitter textSplitter;

    public SpringAiTokenTextSplitterStrategy() {
        this(TokenTextSplitter.builder().build());
    }

    SpringAiTokenTextSplitterStrategy(TokenTextSplitter textSplitter) {
        this.textSplitter = Objects.requireNonNull(textSplitter, "Token 文本切分器不能为空");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(SpringAiDocumentSplitRequest request) {
        return NAME.equals(request.splitterName());
    }

    @Override
    public List<Document> split(SpringAiDocumentSplitRequest request) {
        return textSplitter.apply(request.documents());
    }
}
