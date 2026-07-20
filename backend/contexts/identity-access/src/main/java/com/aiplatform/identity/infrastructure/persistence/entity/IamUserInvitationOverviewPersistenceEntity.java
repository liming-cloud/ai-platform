package com.aiplatform.identity.infrastructure.persistence.entity;

import java.time.Instant;

public class IamUserInvitationOverviewPersistenceEntity {
    private String invitationId; private Long userId; private String email; private String status;
    private String deliveryStatus; private Integer deliveryAttempts; private String deliveryError;
    private Instant expiresAt; private Instant createdAt;
    public String getInvitationId() { return invitationId; } public void setInvitationId(String value) { invitationId = value; }
    public Long getUserId() { return userId; } public void setUserId(Long value) { userId = value; }
    public String getEmail() { return email; } public void setEmail(String value) { email = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getDeliveryStatus() { return deliveryStatus; } public void setDeliveryStatus(String value) { deliveryStatus = value; }
    public Integer getDeliveryAttempts() { return deliveryAttempts; } public void setDeliveryAttempts(Integer value) { deliveryAttempts = value; }
    public String getDeliveryError() { return deliveryError; } public void setDeliveryError(String value) { deliveryError = value; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant value) { expiresAt = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}
