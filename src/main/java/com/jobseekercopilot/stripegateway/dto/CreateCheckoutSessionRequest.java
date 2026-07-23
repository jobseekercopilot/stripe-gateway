package com.jobseekercopilot.stripegateway.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCheckoutSessionRequest {
    @NotBlank
    private String userId;

    @NotBlank
    private String pricingPlanId;

    @Min(1)
    private long tokenAmount;

    @Min(1)
    private long priceGbpPence;
}
