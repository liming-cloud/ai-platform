package com.aiplatform.infra.springai.embedding.adapter;

import com.aiplatform.infra.springai.model.factory.SpringAiEmbeddingModelFactory;
import com.aiplatform.infra.springai.model.request.SpringAiModelRuntimeRequest;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;

final class RecordingEmbeddingModelFactory implements SpringAiEmbeddingModelFactory {
    private final float[] response;
    SpringAiModelRuntimeRequest capturedRequest;

    RecordingEmbeddingModelFactory(float[] response) {
        this.response = response;
    }

    @Override
    public boolean supports(SpringAiModelRuntimeRequest request) {
        return true;
    }

    @Override
    public EmbeddingModel create(SpringAiModelRuntimeRequest request, EmbeddingOptions options) {
        this.capturedRequest = request;
        return new FixedEmbeddingModel(response);
    }
}
