package com.aiplatform.modelhub.domain;

/** Canonical provider family and the only authentication mode it accepts. */
record ModelProviderCapability(String canonicalKind, String requiredAuthType) {
}
