package com.jobseekercopilot.stripegateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateCheckoutSessionResponse {
    private String sessionId;
    private String checkoutUrl;
}
