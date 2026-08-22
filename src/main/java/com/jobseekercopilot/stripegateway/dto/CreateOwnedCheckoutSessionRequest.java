package com.jobseekercopilot.stripegateway.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateOwnedCheckoutSessionRequest {
    @NotNull
    private UUID orderId;
}
