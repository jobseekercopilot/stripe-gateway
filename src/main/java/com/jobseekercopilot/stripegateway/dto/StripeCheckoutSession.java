package com.jobseekercopilot.stripegateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StripeCheckoutSession {
    private String id;
    private String url;

    @JsonProperty("payment_intent")
    private String paymentIntent;
}
