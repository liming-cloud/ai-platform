package com.aiplatform.modelhub.application.idempotency;

/** Stable persistence discriminator for idempotent ModelHub create commands. */
public enum ModelCreationResourceType {
    MODEL_PROVIDER,
    MODEL_CONFIG
}
