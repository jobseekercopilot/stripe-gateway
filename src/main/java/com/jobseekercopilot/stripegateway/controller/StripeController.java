package com.jobseekercopilot.stripegateway.controller;

import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionRequest;
import com.jobseekercopilot.stripegateway.dto.CreateCheckoutSessionResponse;
import com.jobseekercopilot.stripegateway.service.StripeGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stripe")
@RequiredArgsConstructor
public class StripeController {
    private final StripeGatewayService stripeGatewayService;

    @PostMapping("/checkout-sessions")
    @Operation(summary = "Create a Stripe Checkout Session for an AI token bundle")
    public ResponseEntity<CreateCheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request) {
        return ResponseEntity.ok(stripeGatewayService.createCheckoutSession(request));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Receive signed Stripe webhooks")
    public ResponseEntity<Void> webhook(
            @RequestHeader(name = "Stripe-Signature", required = false) String stripeSignature,
            @RequestBody String payload) {
        stripeGatewayService.handleWebhook(payload, stripeSignature);
        return ResponseEntity.ok().build();
    }
}
