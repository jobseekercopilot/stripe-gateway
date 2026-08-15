package com.jobseekercopilot.stripegateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BindCheckoutSessionRequest {
    private String stripeSessionId;
}
