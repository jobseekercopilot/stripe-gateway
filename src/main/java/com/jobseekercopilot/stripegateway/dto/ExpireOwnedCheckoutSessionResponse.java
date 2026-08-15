package com.jobseekercopilot.stripegateway.dto;

import java.util.UUID;

public record ExpireOwnedCheckoutSessionResponse(
        UUID orderId,
        String providerSessionId,
        String status) {}
