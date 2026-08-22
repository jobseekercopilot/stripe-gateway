package com.jobseekercopilot.stripegateway.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateOwnedCheckoutSessionResponse {
    private UUID orderId;
    private String sessionId;
    private String url;
    private Instant expiresAt;
    private int promotionBonusDocumentCredits;
    private boolean promotionGuaranteed;
}
