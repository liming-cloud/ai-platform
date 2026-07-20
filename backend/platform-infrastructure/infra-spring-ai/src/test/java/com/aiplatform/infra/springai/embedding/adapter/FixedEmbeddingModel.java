package com.aiplatform.infra.springai.embedding.adapter;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

final class FixedEmbeddingModel implements EmbeddingModel {
    private final float[] response;

    FixedEmbeddingModel(float[] response) {
        this.response = response;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return new EmbeddingResponse(List.of(new Embedding(response.clone(), 0)));
    }

    @Override
    public float[] embed(Document document) {
        return response.clone();
    }
}
