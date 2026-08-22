package com.jobseekercopilot.stripegateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StripeReadinessResponse {
    private boolean checkoutAvailable;
    @Schema(allowableValues = {
            "READY", "PAYMENTS_DISABLED", "LIVE_RELEASE_NOT_AUTHORISED",
            "STRIPE_CATALOG_NOT_CONFIGURED"
    })
    private String code;
    @Schema(allowableValues = {"LIVE", "FIXTURE", "DISABLED"})
    private String mode;
}
