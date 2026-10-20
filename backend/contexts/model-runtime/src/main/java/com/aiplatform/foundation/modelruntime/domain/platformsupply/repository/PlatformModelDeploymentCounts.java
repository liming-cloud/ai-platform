package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

/** 平台部署全目录的安全计数，不包括端点、租户内容或凭据。 */
public record PlatformModelDeploymentCounts(long total, long drafts, long verified, long published, long disabled) { }
