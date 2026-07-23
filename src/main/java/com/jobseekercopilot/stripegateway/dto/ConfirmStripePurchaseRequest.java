package com.jobseekercopilot.stripegateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfirmStripePurchaseRequest {
    private String userId;
    private String pricingPlanId;
    private long tokenAmount;
    private String stripeSessionId;
    private String stripePaymentIntentId;
}
