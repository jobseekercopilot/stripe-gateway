package com.jobseekercopilot.stripegateway.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class PaymentOrderSnapshot {
    private UUID orderId;
    private String status;
    private String ownerId;
    private String catalogVersion;
    private String pricingPlanId;
    private String pricingPlanName;
    private int documentCredits;
    private int promotionBonusDocumentCredits;
    private boolean promotionGuaranteed;
    private long priceMinor;
    private String currency;
    private String billingCountry;
    private String taxTreatment;
    private String taxStatus;
    private String legalEntityType;
    private String legalEntityConfigurationVersion;
    private boolean displayedPriceIsCheckoutTotal;
    private Instant expiresAt;
    private String stripeSessionId;
}
