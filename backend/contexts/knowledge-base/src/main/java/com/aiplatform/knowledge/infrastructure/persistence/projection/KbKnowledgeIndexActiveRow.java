package com.aiplatform.knowledge.infrastructure.persistence.projection;

public class KbKnowledgeIndexActiveRow {
    private String embeddingModel;
    private String activeIndexVersion;

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getActiveIndexVersion() {
        return activeIndexVersion;
    }

    public void setActiveIndexVersion(String activeIndexVersion) {
        this.activeIndexVersion = activeIndexVersion;
    }
}
