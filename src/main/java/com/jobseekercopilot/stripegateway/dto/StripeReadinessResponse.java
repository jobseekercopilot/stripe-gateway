package com.jobseekercopilot.stripegateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StripeReadinessResponse {
    private boolean checkoutAvailable;
    private String code;
    private String mode;
}
