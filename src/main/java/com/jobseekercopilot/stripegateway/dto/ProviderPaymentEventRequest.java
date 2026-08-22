package com.jobseekercopilot.stripegateway.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderPaymentEventRequest {
    private String providerEventId;
    private String eventType;
    private String payloadSha256;
    private UUID orderId;
    private String stripeSessionId;
    private String paymentIntentId;
    private String paymentStatus;
    private String checkoutStatus;
    private String currency;
    private Long amountTotalMinor;
    private String billingCountry;
    private Boolean liveMode;
    private Instant eventCreatedAt;
    private Long reversalAmountMinor;
}
