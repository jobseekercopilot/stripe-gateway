package com.jobseekercopilot.stripegateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StripeCheckoutSession {
    private String id;
    private String url;
    private String status;

    @JsonProperty("payment_status")
    private String paymentStatus;

    @JsonProperty("expires_at")
    private Long expiresAt;

    @JsonProperty("payment_intent")
    private String paymentIntent;
}
