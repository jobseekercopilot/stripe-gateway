package com.jobseekercopilot.stripegateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ExpireOwnedCheckoutSessionRequest(
        @NotNull UUID orderId,
        @NotBlank String providerSessionId) {}
